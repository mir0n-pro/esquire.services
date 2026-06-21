/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: record {provider, endpoint, destination, topic, params} -- the bound wire of one
 *                   x-Rod leg: the provider NAME (resolved by TransportProviders), the broker endpoint, the
 *                   destination, its kind (topic vs queue), and the provider's own params group.
 * 06/17/2026 mir0n  refinedWith(BusNode) added: the base wire refined with an R&R node (node owns destination /
 *                   topic / params; the base owns provider / endpoint)
 * 06/21/2026 mir0n  the topic component + topicOrFalse() removed; refinedWith(BusNode) no longer carries topic
 *                   (queue-vs-topic is now the ActiveMQ pubSubDomain param)
 */
package pro.mir0n.esquire.messaging;

import java.util.Map;

/** The wire of an x-Rod leg (bound from {@code x-rod.transport.*}): one destination + its provider/endpoint/
 *  params. A service-level override replaces it whole (it is one GROUP -- see {@link XRodParams#merge}). For
 *  an R&R leg, XRodRR builds a per-node effective transport off this base (see XRodRR). A vendor knob (e.g. the
 *  JMS {@code pubSubDomain} flag) rides {@code params} -- the provider reads it, the bus stays neutral. */
public record BusTransport(String provider, String endpoint, String destination,
                           Map<String, String> params) {

    public Map<String, String> paramsOrEmpty() {
        return params != null ? params : Map.of();
    }

    /** This base wire refined with an R&R {@link BusNode}: {@code provider} / {@code endpoint} stay (the base
     *  owns the wire); {@code destination} / {@code params} come from the node when it sets them, else the
     *  base's. The per-field fallback IS the per-group overlay (a node provides a group whole). */
    public BusTransport refinedWith(BusNode node) {
        return new BusTransport(provider, endpoint,
                node.destination() != null ? node.destination() : destination,
                node.params() != null ? node.params() : params);
    }
}
