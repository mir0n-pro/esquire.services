/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/21/2026 mir0n  created: the per-service messaging FACADE -- the ONE messaging API. It owns its bus catalog,
 *                   resolves a logical bus key to a leg, builds the leg's x-rod (paused) and sets its receive
 *                   worker in one call (build); start() runs every built rod (so the whole bus is wired before
 *                   any traffic), close() shuts them down. A per-service messaging class does ALL its bus
 *                   manipulation through this one object. (Absorbs the former XRodManager.)
 * 06/22/2026 mir0n  the facade is a SINGLETON with a two-phase lifecycle. init(Environment) loads+validates the
 *                   catalog and BUILDS every bus ref that DECLARES a role (esquire.<key>.messaging-bus.role) into a
 *                   busKey -> x-rod map, PAUSED; a role-declared ref with no leg fails fast (uses-bus -> topology
 *                   must define it). getXRod(busKey) hands a rod to a publisher; start() runs every built rod
 *                   (called once everything is ready); close() shuts them down. No worker/role build args -- role
 *                   comes from config, the worker is set on the rod by the owner.
 * 06/22/2026 mir0n  health() added: a busKey -> TransportHealth map (each built rod's health()), the source the
 *                   bus health indicator forwards to /actuator/health.
 * 06/23/2026 mir0n  one per-service idle ticker (scheduleWithFixedDelay, daemon "messaging-idle") firing IXRod.idle()
 *                   on every rod; start()/close() manage it; idleSweep() catches Throwable per rod, logs on the develop tier
 */
package pro.mir0n.esquire.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.messaging.catalog.BusRef;
import pro.mir0n.esquire.messaging.catalog.MessagingBusCatalog;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The per-service messaging facade: the single messaging API, one instance per service ({@link #getInstance}).
 * Two phases:
 * <ul>
 *   <li>{@link #init} (the Environment is ready, the context is not) -- load + validate the bus
 *       {@link MessagingBusCatalog}, then for every service-level ref that DECLARES a role
 *       ({@code esquire.<key>.messaging-bus.role}) resolve the leg, create its x-rod (by {@code rod-class})
 *       PAUSED and track it under its bus key. A role-declared ref with no leg fails fast: a service that
 *       says it uses a bus REQUIRES the topology to define it.</li>
 *   <li>{@link #start} (everything is ready) -- RUN every built rod (engine threads + transport delivery): the
 *       bus moves traffic only now. The owner sets each rod's receive worker (via {@link IXRod#setWorker}) before
 *       this.</li>
 * </ul>
 * {@link #getXRod} hands a built rod to a publisher; {@link #close} shuts them all down.
 * <p>
 * The facade is a singleton ({@link #getInstance}); it does NOT dictate how a service consumes it. A service
 * uses {@code getInstance()} directly, or -- if it prefers dependency injection -- declares its own {@code @Bean}
 * delegating to {@code getInstance()} and wires that. Singleton-vs-bean is the service's free choice, not a
 * framework matter.
 */
public class MessagingBus implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MessagingBus.class);
    /** Develop-tier logger for the idle / maintenance ticker (a swallowed rod maintenance failure is a dev
     *  diagnostic; the resulting health DOWN is what surfaces on the console). */
    private static final Logger devLog = LoggerFactory.getLogger("develop.messaging.idle");

    /** Matches a role-declaring ref: {@code esquire.<busKey>.messaging-bus.role} -> group 1 = the bus key. */
    private static final Pattern ROLE_KEY = Pattern.compile("^esquire\\.([^.]+)\\.messaging-bus\\.role$");

    /** A bare {@code rod-class} resolves under this package (a built-in x-rod); a dotted value is a full class name. */
    private static final String ROD_PACKAGE_PREFIX = "pro.mir0n.esquire.messaging.xrod.impl.";
    /** The default x-rod when a leg names no {@code rod-class}: the full transceiver XRod. */
    private static final String DEFAULT_ROD_CLASS = "XRod";

    private static final MessagingBus INSTANCE = new MessagingBus();

    /** The one facade for this service. */
    public static MessagingBus getInstance() {
        return INSTANCE;
    }

    /** The idle / maintenance tick DELAY: ONE service-level ticker waits this long BETWEEN the end of one sweep
     *  and the start of the next (scheduleWithFixedDelay -- guaranteed gap, never back-to-back), so a slow sweep
     *  never hammers. It is the polling resolution, not the heartbeat rate -- a rod's {@code heartbeat-interval}
     *  (>= this) governs the actual rate. The bus runs one maintenance thread, not one per rod. */
    private static final long IDLE_TICK_MS = 1000L;

    private final ObjectMapper objectMapper = new ObjectMapper();   // the wire codec for every rod
    private final Map<String, IXRod> rods = new ConcurrentHashMap<>();   // busKey -> built rod (paused until start)
    private Environment environment;
    private MessagingBusCatalog catalog;
    private ScheduledExecutorService idleTicker;   // the single per-service idle / maintenance thread

    /** Package-private (not part of the public API): production code uses {@link #getInstance()} -- the single
     *  instance. Default access is the test hook: a same-package unit test builds a fresh, isolated facade. */
    MessagingBus() {
    }

    /**
     * INIT -- the build half of the bus lifecycle, in order:
     * <ol>
     *   <li>load + validate the bus {@link MessagingBusCatalog};</li>
     *   <li>{@link #buildRods(String[] busRefCodes) } -- configure + validate every role-declared rod (no comms yet);</li>
*   <li>{@link #initRods()} -- init communications on every rod (create its legs, PAUSED).</li>
     * </ol>
     * The other two steps run later: the receiver adapters set their worker as they construct, and the owner
     * calls {@link #start()} once everything is ready. The Environment is taken the first time.
     */
    public synchronized void init(Environment environment) {
        // SCAN form: discover the buses to build from config (every esquire.<key>.messaging-bus.role). Throws on a
        // discovered ref whose slot is undefined, same as the explicit form.
        init(environment, roleDeclaredBusKeys(environment).toArray(new String[0]));
    }

    /** EXPLICIT form: the service names the buses it uses. Every code MUST resolve to a configured leg (with a
     *  role) -- a missed/undefined slot throws here, the first time. To turn a bus OFF, declare it disabled
     *  (rod-class XRodDisabled / repoint its bus-id to the catalog "disabled" bus), do NOT just omit it. */
    public synchronized void init(Environment environment, String[] busRefCodes) {
        this.environment = environment;
        this.catalog = new MessagingBusCatalog(environment);
        catalog.load();         // 1. load + fail-fast validate the catalog
        buildRods(busRefCodes); // 2. configure + validate every named/discovered rod (no comms) -- throws on a missed slot
        initRods();             // 3. init communications on every rod (create legs, paused)
    }

    /** Build phase: configure + validate each bus code's x-rod into the {@code busKey -> rod} map (no comms
     *  opened yet). A code whose slot is undefined (or declares no role) THROWS -- a missed bus is a boot failure. */
    private void buildRods(String[] busRefCodes) {
        for (String busKey : busRefCodes) {
            rods.put(busKey, buildRod(busKey, busRef(busKey)));
        }
        log.info("messaging facade: built {} x-rod(s) {}", rods.size(), rods.keySet());
    }

    /** Comms-init phase: init every built rod -- create its legs (PAUSED; nothing flows until {@link #start()}).
     *  Separate from build so the whole catalog is built + validated before any leg opens. */
    private void initRods() {
        rods.forEach((busKey, rod) -> rod.init(busKey, devLog(busKey)));
        log.info("messaging facade: initialized comms on {} x-rod(s)", rods.size());
    }

    /** The built rod for {@code busKey}. A key that was never built -- a service asking for a bus it did not
     *  declare (or that init skipped) -- THROWS: it is a wiring bug, not optional non-participation. To run a
     *  service WITHOUT a bus, declare that bus disabled in config (rod-class XRodDisabled); it is then built (an
     *  in-map no-op rod) and returns normally. A bus is never silently absent. */
    public IXRod getXRod(String busKey) {
        IXRod rod = rods.get(busKey);
        if (rod == null) {
            throw new IllegalStateException("messaging facade: x-rod '" + busKey + "' is NOT built -- the service "
                    + "did not declare this bus (declare it disabled, rod-class XRodDisabled, to run without it)");
        }
        return rod;
    }

    /** START phase: RUN every built rod (engine threads + transport delivery), then start the single idle /
     *  maintenance ticker. Called once everything is ready (storages, roles, the context) and every rod's worker
     *  is set. */
    public synchronized void start() {
        rods.values().forEach(IXRod::start);
        startIdleTicker();
        log.info("messaging facade: started {} x-rod(s)", rods.size());
    }

    /** Stop the idle ticker first (no new maintenance/heartbeat enqueued during teardown), then shut every built
     *  rod down (in-flight work drains). */
    @Override
    public synchronized void close() {
        if (idleTicker != null) {
            idleTicker.shutdownNow();
            idleTicker = null;
        }
        rods.values().forEach(IXRod::shutdown);
    }

    /** Start the ONE per-service idle ticker: every {@link #IDLE_TICK_MS} it fires {@link IXRod#idle()} on every
     *  rod (the alive-protocol heartbeat cadence today; the seam for future transport housekeeping). A rod that
     *  throws does not break the sweep. A daemon thread, so it never holds the JVM open. */
    private void startIdleTicker() {
        this.idleTicker = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("messaging-idle").factory());
        // fixed DELAY (not fixed rate): a full IDLE_TICK_MS gap between the end of one sweep and the start of the
        // next, so sweeps never run back-to-back / pile up if one is slow.
        idleTicker.scheduleWithFixedDelay(this::idleSweep, IDLE_TICK_MS, IDLE_TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** One idle pass over every rod; a single rod's failure is logged and does not stop the others. Catches
     *  Throwable on purpose: an uncaught Throwable escaping a {@code scheduleAtFixedRate} task SILENTLY cancels
     *  the whole periodic ticker (no further runs) -- one rod must never be able to kill the service's idle loop. */
    private void idleSweep() {
        for (IXRod rod : rods.values()) {
            try {
                rod.idle();
            } catch (Throwable ex) {
                devLog.error("messaging idle ticker: rod maintenance failed: {}", ex.toString());
            }
        }
    }

    /** The connection health of every built bus ({@code busKey -> health}) -- the source the bus health
     *  indicator forwards to {@code /actuator/health}. An in-process / disabled bus reports UP (no broker); a
     *  transport-backed bus reports its x-rod's worst leg (UP / DOWN / UNKNOWN). */
    public Map<String, TransportHealth> health() {
        Map<String, TransportHealth> ret = new LinkedHashMap<>();
        rods.forEach((busKey, rod) -> ret.put(busKey, rod.health()));
        return ret;
    }

    // ------------------------------------------------------------------ build

    /**
     * Build (configure + validate) the x-rod for {@code busKey} as the ref's {@code role}: resolve the leg,
     * resolve the x-rod (by {@code rod-class}), {@code validate} + {@code configure} it -- but DO NOT open any
     * comms (that is {@link #initRods()}). The x-rod builds its OWN transport from the leg. A role-declared ref
     * with NO leg is a misconfiguration -- a service that declares it uses a bus requires the topology to define it.
     */
    private IXRod buildRod(String busKey, BusRef ref) {
        Role role = ref.role();
        if (role == null) {
            throw new IllegalStateException("messaging-bus[" + busKey + "]: no role declared "
                    + "(esquire." + busKey + ".messaging-bus.role) -- a bus a service uses must declare its role");
        }
        String busId = ref.busIdOr(busKey);
        String slotId = ref.slotId();
        XRodParams base = resolveParams(ref, busId, slotId);
        if (base == null) {
            throw new IllegalStateException("messaging-bus[" + busKey + "]: bus-id=" + busId + " slot-id=" + slotId
                    + " declares role=" + role + " but no leg defines it -- the topology must define a bus a service uses");
        }
        XRodParams eff = base.withBus(busId, slotId, instanceId());
        IXRod rod = resolveRod(eff.rodClassOr(DEFAULT_ROD_CLASS));
        rod.validate(eff);                          // fail-fast on this x-rod's required leg params
        rod.configure(eff, role, objectMapper);
        return rod;                                 // built + configured, comms NOT yet opened (initRods does that)
    }

    /** Create a fresh x-rod for {@code rodClass}: a value WITH a dot is a full class name (a custom x-rod anywhere);
     *  a bare name follows the built-in convention {@code messaging.xrod.impl.<name>}; blank -> the default XRod.
     *  Throws a clear error if the class is absent or is not an {@link IXRod}. */
    private static IXRod resolveRod(String rodClass) {
        String p = (rodClass == null || rodClass.isBlank()) ? DEFAULT_ROD_CLASS : rodClass.trim();
        String fqcn = p.indexOf('.') >= 0 ? p : ROD_PACKAGE_PREFIX + p;
        IXRod ret;
        try {
            Object o = Class.forName(fqcn).getDeclaredConstructor().newInstance();
            if (!(o instanceof IXRod rod)) {
                throw new IllegalStateException(fqcn + " does not implement IXRod");
            }
            ret = rod;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no x-rod class " + fqcn + " on the classpath", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate x-rod " + fqcn, e);
        }
        return ret;
    }

    // ------------------------------------------------------------------ leg / ref resolution

    /** The effective leg params: the catalog leg merged with a service-level {@code x-rod} on the ref -- any GROUP
     *  the service sets replaces the base's whole group. Catalog-only or service-only both work; {@code null} when
     *  NEITHER defines it. */
    private XRodParams resolveParams(BusRef ref, String busId, String slotId) {
        XRodParams base = catalog.find(busId, slotId);
        XRodParams over = ref.xRod() != null ? XRodParams.from(ref.xRod()) : null;
        return base != null ? base.merge(over) : over;
    }

    /** Every bus key whose service-level ref declares a {@code role} (the refs to BUILD): scan the Environment
     *  for {@code esquire.<key>.messaging-bus.role} property names. A ref with no role is NOT built here (e.g.
     *  audit, driven by its own procedure). */
    private static Set<String> roleDeclaredBusKeys(Environment environment) {
        Set<String> ret = new LinkedHashSet<>();
        if (environment instanceof ConfigurableEnvironment ce) {
            for (PropertySource<?> source : ce.getPropertySources()) {
                if (source instanceof EnumerablePropertySource<?> eps) {
                    for (String propertyName : eps.getPropertyNames()) {
                        Matcher m = ROLE_KEY.matcher(propertyName);
                        if (m.matches()) {
                            ret.add(m.group(1));
                        }
                    }
                }
            }
        }
        return ret;
    }

    /** The default rod-id when a leg sets none: the per-instance id {@code <app>.<instanceNo>} (each replica owns
     *  a distinct rod-id, so an R&R CLIENT's {@code RodID} selector isolates its own responses). */
    private String instanceId() {
        String app = environment.getProperty("spring.application.name");
        return (app != null ? app : "") + "." + EsqUtils.instanceNo();
    }

    /** Resolve a logical bus key: the service-level ref esquire.&lt;key&gt;.messaging-bus -> {bus-id, slot-id,
     *  role, x-rod}. */
    private BusRef busRef(String busKey) {
        return Binder.get(environment).bind("esquire." + busKey + ".messaging-bus", Bindable.of(BusRef.class))
                .orElseGet(() -> new BusRef(busKey, null, null, null));
    }

    private static Logger devLog(String name) {
        return LoggerFactory.getLogger("develop.xrod." + name);
    }
}
