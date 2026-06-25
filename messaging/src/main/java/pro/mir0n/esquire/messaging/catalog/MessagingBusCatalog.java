/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the messaging-bus CATALOG -- the union of the shared esquire.messaging-bus topology
 *                   and a service's own esquire.<app>-messaging-bus, bound from the Environment. resolve/find a
 *                   leg by {bus-id, slot-id} -> XRodParams (the BASE); publishLeg / consumeLeg build the lower-level
 *                   transport settings (PublishSettings / ConsumeSettings via the resolved provider) as a leg binding.
 * 06/17/2026 mir0n  consumeLeg(busId, slotId, om) -- the Role parameter + selector dropped (whole-node, selector
 *                   null; a selector is the x-rod's concern); find() warns on a duplicate (bus-id, slot-id)
 * 06/18/2026 mir0n  the service overlay moved to the service namespace: binds <spring.application.name>.messaging-bus
 *                   (was esquire.<app>-messaging-bus), beside the service's other config; nothing else changed
 * 06/21/2026 mir0n  consumeLeg() builds ConsumeSettings without the topic argument (topic dropped from the
 *                   transport settings)
 * 06/21/2026 mir0n  validate() fails fast on a duplicate bus-id (catalog) / slot-id (within a bus) / node-id
 *                   (within an x-rod's nodes), run PER SOURCE; the service overlay MERGES onto the shared
 *                   catalog by id (a same-id bus/slot replaces, a new one is added); find() returns the FIRST
 *                   match (the warn-and-take-last dropped).
 * 06/22/2026 mir0n  moved to messaging.catalog (was messaging). The bind+merge moved out of the constructor into
 *                   an explicit synchronized load() (buses null until load(), buses() accessor throws if unloaded)
 *                   so the bus lifecycle runs it in its init phase; consumeSelector doc BROADCAST -> single-node;
 *                   javadoc XRodManager -> MessagingBus.
 */
package pro.mir0n.esquire.messaging.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.TransportProviders;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves messaging-bus legs and builds their transport settings. The catalog is the shared cross-service
 * {@code esquire.messaging-bus} (imported from the one topology file) MERGED with a service's OWN overlay,
 * declared in its application.yml under its OWN namespace {@code <spring.application.name>.messaging-bus}
 * (e.g. {@code enyman.messaging-bus} -- its own in-process leg, whose datasource is service-specific).
 * The overlay REPLACES a shared bus/slot with the same id (and adds new ones); both are full legs and bind
 * identically. The service namespace keeps the key clear of the {@code esquire.<bus-key>.messaging-bus} refs.
 * A service-level x-rod ref override is layered on top at resolve time (see {@code MessagingBus}).
 *
 * <p>A leg is named by {@code (bus-id, slot-id)}; its {@link XRodParams} is the BASE. For a feature that wires
 * its OWN consumer (one that already owns a worker pool), the catalog returns a
 * {@link ConsumeLeg} -- the resolved provider + destination + the ready settings -- which the feature feeds to
 * {@code RodTransportAdapter} to attach the RodEvent codec. (The regular producer/consumer path is {@code XRod}.)
 */
public class MessagingBusCatalog {

    private static final int DEFAULT_CONCURRENCY = 1;

    private final Environment environment;
    private List<MessagingBus> buses;   // null until load()

    public MessagingBusCatalog(Environment environment) {
        this.environment = environment;
    }

    /**
     * Load + validate the catalog: bind the shared topology ({@code esquire.messaging-bus}) and MERGE this
     * service's overlay ({@code <app>.messaging-bus}) onto it BY ID -- a service bus REPLACES the shared bus with
     * the same bus-id (a service slot replaces the shared slot with the same slot-id; a new bus/slot is added).
     * Bound as two lists, NOT a single key across two sources (Spring binds lists by INDEX, so a higher-precedence
     * source would replace the WHOLE list instead of merging). Each source is validated for internal uniqueness;
     * a cross-source same-id is the intended replace, not a duplicate. EXPLICIT (not in the constructor) so the
     * bus lifecycle runs it in its init phase; idempotent.
     */
    public synchronized void load() {
        if (buses == null) {
            Binder binder = Binder.get(environment);
            List<MessagingBus> all = new ArrayList<>(
                    binder.bind("esquire.messaging-bus", Bindable.listOf(MessagingBus.class)).orElseGet(List::of));
            validate(all);   // the shared catalog is internally consistent (unique bus / slot / node ids)
            String app = environment.getProperty("spring.application.name");
            if (app != null && !app.isBlank()) {
                List<MessagingBus> overlay = binder.bind(app + ".messaging-bus",
                        Bindable.listOf(MessagingBus.class)).orElseGet(List::of);
                validate(overlay);            // the overlay is internally consistent too
                mergeOverlay(all, overlay);   // overlay REPLACES the shared catalog by bus-id / slot-id
            }
            this.buses = all;
        }
    }

    /** The loaded catalog; throws if {@link #load()} has not run. */
    private List<MessagingBus> buses() {
        if (buses == null) {
            throw new IllegalStateException("messaging-bus catalog not loaded -- call load() first");
        }
        return buses;
    }

    /**
     * Fail-fast functional validation of ONE bus list (the shared catalog OR a service overlay, each validated
     * on its own before the merge). The list is a yaml LIST used AS A MAP, so the keys that index it must be
     * unique: a {@code bus-id} across the list, a {@code slot-id} within one bus, and a {@code node-id} within
     * one x-rod's {@code transport.nodes}. Throws on the first duplicate. (A bus/slot a service references but
     * the catalog does not define is NOT a failure -- the frontend disables that leg.)
     */
    private static void validate(List<MessagingBus> buses) {
        Set<String> busIds = new LinkedHashSet<>();
        for (MessagingBus bus : buses) {
            if (!busIds.add(bus.busId())) {
                throw new IllegalStateException("messaging-bus catalog: duplicate bus-id=" + bus.busId()
                        + " -- a bus-id must be unique across the catalog");
            }
            Set<String> slotIds = new LinkedHashSet<>();
            if (bus.slots() != null) {
                for (BusSlot slot : bus.slots()) {
                    if (!slotIds.add(slot.slotId())) {
                        throw new IllegalStateException("messaging-bus bus-id=" + bus.busId()
                                + ": duplicate slot-id=" + slot.slotId() + " -- a slot-id must be unique within a bus");
                    }
                    Set<String> nodeIds = new LinkedHashSet<>();
                    for (BusNode node : XRodParams.from(slot.xRod()).nodes()) {
                        if (node.nodeId() != null && !nodeIds.add(node.nodeId())) {
                            throw new IllegalStateException("messaging-bus bus-id=" + bus.busId() + " slot-id="
                                    + slot.slotId() + ": duplicate node-id=" + node.nodeId()
                                    + " -- a node-id must be unique within an x-rod's nodes");
                        }
                    }
                }
            }
        }
    }

    /** Merge the service overlay onto the shared catalog IN PLACE: a service bus REPLACES the shared bus with
     *  the same bus-id (its slots merged in -- a service slot replaces the shared slot with the same slot-id, a
     *  NEW slot-id is added); a service bus with a NEW bus-id is appended. The service wins on every id clash. */
    private static void mergeOverlay(List<MessagingBus> shared, List<MessagingBus> overlay) {
        for (MessagingBus svcBus : overlay) {
            int i = indexOfBus(shared, svcBus.busId());
            if (i < 0) {
                shared.add(svcBus);
            } else {
                shared.set(i, mergeSlots(shared.get(i), svcBus));
            }
        }
    }

    /** {@code shared} with {@code overlay}'s slots merged in: a same slot-id REPLACES, a new slot-id is added. */
    private static MessagingBus mergeSlots(MessagingBus shared, MessagingBus overlay) {
        List<BusSlot> slots = new ArrayList<>(shared.slots() != null ? shared.slots() : List.of());
        if (overlay.slots() != null) {
            for (BusSlot svcSlot : overlay.slots()) {
                int i = indexOfSlot(slots, svcSlot.slotId());
                if (i < 0) {
                    slots.add(svcSlot);
                } else {
                    slots.set(i, svcSlot);
                }
            }
        }
        return new MessagingBus(shared.busId(), slots);
    }

    private static int indexOfBus(List<MessagingBus> buses, String busId) {
        int ret = -1;
        for (int i = 0; i < buses.size(); i++) {
            if (Objects.equals(buses.get(i).busId(), busId)) {
                ret = i;
                break;
            }
        }
        return ret;
    }

    private static int indexOfSlot(List<BusSlot> slots, String slotId) {
        int ret = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (Objects.equals(slots.get(i).slotId(), slotId)) {
                ret = i;
                break;
            }
        }
        return ret;
    }

    /** The BASE x-Rod params for a leg; throws if the catalog has no such {bus-id, slot-id}. */
    public XRodParams resolve(String busId, String slotId) {
        XRodParams ret = find(busId, slotId);
        if (ret == null) {
            throw new IllegalStateException(
                    "messaging-bus catalog has no leg bus-id=" + busId + " slot-id=" + slotId);
        }
        return ret;
    }

    /** The BASE x-Rod params for a leg, or {@code null} if the catalog has no such {bus-id, slot-id} (the
     *  config may live at the service level only -- the frontend layers a service-level override on top). */
    public XRodParams find(String busId, String slotId) {
        XRodParams ret = null;
        for (MessagingBus bus : buses()) {
            if (busId.equals(bus.busId()) && bus.slots() != null) {
                for (BusSlot slot : bus.slots()) {
                    if (slotId.equals(slot.slotId())) {
                        ret = XRodParams.from(slot.xRod());   // FIRST match wins; uniqueness is enforced at construction
                        break;
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Resolve a leg and build its consume-side binding for the WHOLE node -- no message selector. A selector is
     * the x-rod's concern, not the catalog's: {@code XRodRR} computes the R&R selector (a CLIENT filters to its own
     * {@code RodID}, a SERVER to its {@code SlotID} -- many slots can share one node). A single-node consumer that
     * needs to filter would take a configurable selector (TODO). This catalog path (a feature that
     * hand-wires its own consumer rather than going through an x-rod) consumes the whole node.
     */
    public ConsumeLeg consumeLeg(String busId, String slotId, ObjectMapper om) {
        XRodParams p = resolve(busId, slotId);
        BusTransport t = requireTransport(p, busId, slotId);
        ITransportProvider provider = TransportProviders.resolve(t.provider());
        ConsumeSettings settings = new ConsumeSettings(om, t.endpoint(),
                new BusIdentity(busId, slotId, p.rodId()), t.paramsOrEmpty(),
                p.concurrencyOr(DEFAULT_CONCURRENCY), null);
        return new ConsumeLeg(provider, t.destination(), settings);
    }

    private static BusTransport requireTransport(XRodParams p, String busId, String slotId) {
        BusTransport t = p.transport();
        boolean ok = t != null && t.provider() != null && t.destination() != null;
        if (!ok) {
            throw new IllegalStateException("messaging-bus leg bus-id=" + busId + " slot-id=" + slotId
                    + " needs x-rod.transport.{provider, destination}");
        }
        return t;
    }

    /** Consume-side binding: the resolved provider, the destination, and the ready {@link ConsumeSettings}. */
    public record ConsumeLeg(ITransportProvider provider, String destination, ConsumeSettings settings) {
    }
}
