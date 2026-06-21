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
 */
package pro.mir0n.esquire.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.TransportProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves messaging-bus legs and builds their transport settings. The catalog is the UNION of the two places
 * a leg may be DEFINED: the shared cross-service {@code esquire.messaging-bus} (imported from the one topology
 * file) and a service's OWN overlay, declared in its application.yml under its OWN namespace
 * {@code <spring.application.name>.messaging-bus} (e.g. {@code enyman.messaging-bus} -- its in-process
 * audit-b leg, whose datasource is service-specific). Both are full legs and bind identically; the service
 * namespace keeps the key clear of the {@code esquire.<bus-key>.messaging-bus} refs. A service-level x-rod ref
 * override is layered on top at resolve time (see {@code XRodManager}).
 *
 * <p>A leg is named by {@code (bus-id, slot-id)}; its {@link XRodParams} is the BASE. For a feature that wires
 * its OWN consumer (e.g. xxRod's director, which already owns a worker pool), the catalog returns a
 * {@link ConsumeLeg} -- the resolved provider + destination + the ready settings -- which the feature feeds to
 * {@code RodTransportAdapter} to attach the RodEvent codec. (The regular producer/consumer path is {@code XRod}.)
 */
public class MessagingBusCatalog {

    private static final int DEFAULT_CONCURRENCY = 1;
    private static final Logger log = LoggerFactory.getLogger(MessagingBusCatalog.class);

    private final List<MessagingBus> buses;

    public MessagingBusCatalog(Environment environment) {
        Binder binder = Binder.get(environment);
        // the catalog = the global topology (topology.yml -> esquire.messaging-bus) UNION this service's OWN
        // overlay, declared under its OWN namespace <app>.messaging-bus (e.g. enyman.messaging-bus, beside the
        // service's other config). Concatenated in code, NOT a single esquire.messaging-bus key across two
        // property sources -- Spring binds lists by INDEX, so a higher-precedence source replaces the whole
        // list instead of appending. Under the service namespace the key is clear of the esquire.<bus-key>
        // .messaging-bus ref shape, so no hyphen workaround is needed.
        List<MessagingBus> all = new ArrayList<>(
                binder.bind("esquire.messaging-bus", Bindable.listOf(MessagingBus.class)).orElseGet(List::of));
        String app = environment.getProperty("spring.application.name");
        if (app != null && !app.isBlank()) {
            all.addAll(binder.bind(app + ".messaging-bus",
                    Bindable.listOf(MessagingBus.class)).orElseGet(List::of));
        }
        this.buses = all;
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
        int matches = 0;
        for (MessagingBus bus : buses) {
            if (busId.equals(bus.busId()) && bus.slots() != null) {
                for (BusSlot svc : bus.slots()) {
                    if (slotId.equals(svc.slotId())) {
                        ret = XRodParams.from(svc.xRod());
                        matches++;
                    }
                }
            }
        }
        if (matches > 1) {
            // a config mistake (each (bus-id, slot-id) should be unique across the shared topology + the
            // service-local legs); take the last but surface it -- the leg the service runs is otherwise silent.
            log.warn("messaging-bus catalog has {} legs for bus-id={} slot-id={} -- (bus-id, slot-id) should be "
                    + "unique; using the last. Check the topology and the service-local legs for a duplicate.",
                    matches, busId, slotId);
        }
        return ret;
    }

    /**
     * Resolve a leg and build its consume-side binding for the WHOLE node -- no message selector. A selector is
     * the x-rod's concern, not the catalog's: {@code XRodRR} computes the R&R selector (a CLIENT filters to its own
     * {@code RodID}, a SERVER to its {@code SlotID} -- many slots can share one node). A BROADCAST consumer that
     * needs to filter would take a configurable selector (TODO). This catalog path (xxRod's audit director, which
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
