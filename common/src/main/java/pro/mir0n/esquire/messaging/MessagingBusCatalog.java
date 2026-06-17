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
 */
package pro.mir0n.esquire.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves messaging-bus legs and builds their transport settings. The catalog is the UNION of the two places
 * a leg may be DEFINED: the shared cross-service {@code esquire.messaging-bus} (imported from the one topology
 * file) and a service's OWN topology, declared in its application.yml under
 * {@code esquire.<spring.application.name>-messaging-bus} (e.g. {@code esquire.enyman-messaging-bus} -- its
 * in-process audit-b leg, whose log-db is service-specific). Both are full legs and bind identically; the
 * app-name key stays clear of the {@code esquire.<bus-key>.messaging-bus} refs. A service-level x-rod ref
 * override is layered on top at resolve time (see {@code XRodManager}).
 *
 * <p>A leg is named by {@code (bus-id, slot-id)}; its {@link XRodParams} is the BASE. The catalog returns a
 * {@link PublishLeg} / {@link ConsumeLeg} -- the resolved provider + destination + the ready settings -- which
 * the feature feeds to {@code RodTransportAdapter} to attach the RodEvent codec.
 */
public class MessagingBusCatalog {

    private static final int DEFAULT_PUBLISHER_POOL = 0;
    private static final int DEFAULT_CONCURRENCY    = 1;

    private final List<MessagingBus> buses;

    public MessagingBusCatalog(Environment environment) {
        Binder binder = Binder.get(environment);
        // the catalog = the shared topology (topology.yml) UNION this service's own topology (its
        // application.yml). Concatenated in code, NOT a single esquire.messaging-bus key across two property
        // sources -- Spring binds lists by INDEX, so the higher-precedence source replaces the whole list
        // instead of appending. The service-own key carries the app name (esquire.<app>-messaging-bus) to
        // stay clear of the esquire.<bus-key>.messaging-bus refs.
        List<MessagingBus> all = new ArrayList<>(
                binder.bind("esquire.messaging-bus", Bindable.listOf(MessagingBus.class)).orElseGet(List::of));
        String app = environment.getProperty("spring.application.name");
        if (app != null && !app.isBlank()) {
            all.addAll(binder.bind("esquire." + app + "-messaging-bus",
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
        for (MessagingBus bus : buses) {
            if (busId.equals(bus.busId()) && bus.slot() != null) {
                for (BusSlot svc : bus.slot()) {
                    if (slotId.equals(svc.slotId())) {
                        ret = XRodParams.from(svc.xRod());
                    }
                }
            }
        }
        return ret;
    }

    /** Resolve a leg and build the publish-side binding for {@code role} (the node it PRODUCES to). */
    public PublishLeg publishLeg(String busId, String slotId, Role role, ObjectMapper om) {
        XRodParams p = resolve(busId, slotId);
        BusTransport t = requireTransport(p, busId, slotId);
        ITransportProvider provider = TransportProviders.resolve(t.provider());
        PublishSettings settings = new PublishSettings(om, t.endpoint(), null, t.topicOrFalse(),
                new BusIdentity(busId, slotId, p.rodId()), t.paramsOrEmpty(),
                p.publisherPoolSizeOr(DEFAULT_PUBLISHER_POOL));
        return new PublishLeg(provider, t.destination(), settings);
    }

    /**
     * Resolve a leg and build the consume-side binding for {@code role} (the node it CONSUMES from). A CLIENT
     * consuming responses gets a {@code RodID = '<this leg's rod-id>'} selector so each instance only receives the
     * responses it sent the request for; SERVER / BROADCAST consume the whole node, no selector. (NOTE: this is
     * the legacy catalog path -- live R&R uses {@code XRodRR}, which owns the role-driven node + selector.)
     */
    public ConsumeLeg consumeLeg(String busId, String slotId, Role role, ObjectMapper om) {
        XRodParams p = resolve(busId, slotId);
        BusTransport t = requireTransport(p, busId, slotId);
        ITransportProvider provider = TransportProviders.resolve(t.provider());
        // legacy catalog path: CLIENT filters its own responses by rod-id, else the whole node. The role-driven
        // R&R selector (SERVER by slot-id, etc.) lives in XRodRR, which owns the live R&R consume.
        String selector = role == Role.CLIENT
                ? EsqMsgConstants.FIELD_ROD_ID + " = '" + p.rodId() + "'" : null;
        ConsumeSettings settings = new ConsumeSettings(om, t.endpoint(), null, t.topicOrFalse(),
                new BusIdentity(busId, slotId, p.rodId()), t.paramsOrEmpty(),
                p.concurrencyOr(DEFAULT_CONCURRENCY), selector);
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

    /** Publish-side binding: the resolved provider, the destination, and the ready {@link PublishSettings}. */
    public record PublishLeg(ITransportProvider provider, String destination, PublishSettings settings) {
    }

    /** Consume-side binding: the resolved provider, the destination, and the ready {@link ConsumeSettings}. */
    public record ConsumeLeg(ITransportProvider provider, String destination, ConsumeSettings settings) {
    }
}
