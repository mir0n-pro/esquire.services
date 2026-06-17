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
 */
package pro.mir0n.esquire.messaging;

import java.util.Map;

/** The wire of an x-Rod leg (bound from {@code x-rod.transport.*}): one destination + its provider/endpoint/
 *  topic/params. A service-level override replaces it whole (it is one GROUP -- see {@link XRodParams#merge}). For
 *  an R&R leg, XRodRR builds a per-node effective transport off this base (see XRodRR). */
public record BusTransport(String provider, String endpoint, String destination, Boolean topic,
                           Map<String, String> params) {

    public boolean topicOrFalse() {
        return Boolean.TRUE.equals(topic);
    }

    public Map<String, String> paramsOrEmpty() {
        return params != null ? params : Map.of();
    }
}
