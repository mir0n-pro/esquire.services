/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the ONE generic per-service x-Rod frontend (one shared bean). producer(busKey, role) /
 *                   consumer(busKey, role, worker) build a rod in a single call -- resolve the logical bus key to a
 *                   BusRef, merge the catalog leg with any service-level x-rod override, resolve the pod by rod-class
 *                   (the OFF pod when no leg), configure + start it; close() shuts every rod down. An unset leg
 *                   rod-id defaults to the per-instance id <app>.<instanceNo> (EsqUtils.instanceNo()). Registered
 *                   once by XRodAutoConfiguration.
 * 06/17/2026 mir0n  configureXRod() calls rod.validate(eff) before configure / start (fail-fast on the leg's
 *                   required params)
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.messaging.BusRef;
import pro.mir0n.esquire.messaging.MessagingBusCatalog;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The per-service x-Rod frontend: one call builds a producer/consumer rod; close() shuts them all down. */
public class XRodManager implements AutoCloseable {

    private final Environment environment;
    private final MessagingBusCatalog catalog;
    private final ObjectMapper objectMapper;
    private final List<IXRod> rods = new CopyOnWriteArrayList<>();

    public XRodManager(Environment environment, ObjectMapper objectMapper) {
        this.environment  = environment;
        this.catalog      = new MessagingBusCatalog(environment);
        this.objectMapper = objectMapper;
    }

    /**
     * Open a producer on {@code busKey} as {@code role}: resolve the leg + the x-rod (by {@code rod-class}) and
     * configure it. The X-ROD builds its OWN transport from the leg -- {@code XRod} opens a publisher to the leg's
     * destination ({@code XRodRR} picks the role's request/response node); an in-process x-rod (XRodInProcess / XRodInfo)
     * self-wires from its own sub-block. The producer is type-agnostic -- the caller stamps the msg-type on each
     * event ({@code transmit} carries it; {@code post()} takes it as an arg).
     */
    public IXRod producer(String busKey, Role role) {
        return configureXRod(busKey, role, null, true);
    }

    /**
     * Open a consumer on {@code busKey} as {@code role}: a bounded pool hands each event to {@code worker}; the
     * x-rod opens (and owns) the transport consumer for the role's node (a CLIENT filters to its own rod-id).
     */
    public IXRod consumer(String busKey, Role role, Consumer<RodEvent> worker) {
        return configureXRod(busKey, role, worker, false);
    }

    /** Resolve the leg + x-rod and hand it the WHOLE leg config (XRodParams) + role + objectMapper; the x-rod
     *  self-extracts its engine knobs + wire from the params and builds its own transport. The frontend re-packs
     *  nothing -- it only supplies the leg identity (with the rod-id defaulted to the service instance id). */
    private IXRod configureXRod(String busKey, Role role, Consumer<RodEvent> worker, boolean produce) {
        BusRef ref = busRef(busKey);
        String busId = ref.busIdOr(busKey);
        String slotId = ref.slotId();
        String name = label(busKey, role, produce);
        // resolve the leg, fold in the identity (bus-id / slot-id + rod-id default), hand the WHOLE params to
        // the x-rod. configure() prepares; start(worker) runs (worker null = producer, non-null = consumer).
        XRodParams base = resolveParams(ref, busId, slotId);
        IXRod rod;
        if (base == null) {
            // NO leg (neither the catalog nor a service-level ref) -> the OFF x-rod. A missing leg is a disabled
            // slot, never an error; an explicit rod-class = XRodDisabled lands on the SAME x-rod via XRods.resolve.
            rod = XRods.resolve(XRods.DISABLED);
            rod.configure(null, role, objectMapper);
            devLog(name).info("x-rod[{}]: no x-rod config for bus-id={} slot-id={} -> disabled (no-op)", name, busId, slotId);
        } else {
            XRodParams eff = base.withBus(busId, slotId, instanceId());
            rod = XRods.resolve(eff.rodClassOr(XRods.DEFAULT));
            rod.validate(eff);                          // fail-fast on this x-rod's required leg params
            rod.configure(eff, role, objectMapper);
        }
        return start(rod, worker, name);
    }

    /** The effective leg params: the catalog leg merged with a service-level {@code x-rod} on the ref -- any
     *  GROUP the service sets (a scalar, the transport wire, or an x-rod sub-block) replaces the base's whole group
     *  (see {@link XRodParams#merge}). Catalog-only or service-only both work; {@code null} when NEITHER defines
     *  it -- the frontend then runs the OFF x-rod ({@link XRods#DISABLED}). */
    private XRodParams resolveParams(BusRef ref, String busId, String slotId) {
        XRodParams base = catalog.find(busId, slotId);
        XRodParams over = ref.xRod() != null ? XRodParams.from(ref.xRod()) : null;
        return base != null ? base.merge(over) : over;   // service-level groups win in full; null = no leg -> OFF
    }

    /** The default rod-id when a leg sets none: the per-instance id {@code <app>.<instanceNo>} (e.g.
     *  {@code enyman.0}). The instance number is the framework-wide {@link EsqUtils#instanceNo()} -- the
     *  trailing ordinal of this pod/container's host name (the StatefulSet ordinal, or a
     *  {@code hostname: <app>-N} on the Docker container). Each replica thus owns a DISTINCT rod-id so a
     *  CLIENT's {@code RodID = '<rod-id>'} selector isolates that instance's R&R responses (a plain Deployment
     *  with no ordinal in the name resolves to {@code <app>.0} -- run R&R clients as a StatefulSet to scale). */
    private String instanceId() {
        String app = environment.getProperty("spring.application.name");
        return (app != null ? app : "") + "." + EsqUtils.instanceNo();
    }

    private IXRod start(IXRod rod, Consumer<RodEvent> worker, String name) {
        rod.start(name, devLog(name), worker);
        rods.add(rod);
        return rod;
    }

    /** Resolve a logical bus key: the service-level ref esquire.&lt;key&gt;.messaging-bus -> {bus-id, slot-id}
     *  (a DOTTED key is a catalog bus-id directly, no ref). */
    private BusRef busRef(String busKey) {
        BusRef ret;
        if (busKey.indexOf('.') >= 0) {
            ret = new BusRef(busKey, null, null);
        } else {
            ret = Binder.get(environment).bind("esquire." + busKey + ".messaging-bus", Bindable.of(BusRef.class))
                    .orElseGet(() -> new BusRef(busKey, null, null));
        }
        return ret;
    }

    private static String label(String busKey, Role role, boolean produce) {
        return busKey + (produce ? ".out" : ".in") + (role == Role.BROADCAST ? "" : "." + role.name().toLowerCase());
    }

    @Override
    public void close() {
        rods.forEach(IXRod::shutdown);
    }

    private static Logger devLog(String name) {
        return LoggerFactory.getLogger("develop.xrod." + name);
    }
}
