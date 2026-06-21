/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the transport-neutral message moved by an ITransportProvider -- a property-bag
 *                   envelope (headers) plus an optional routing / partition key; the provider maps it onto its
 *                   own wire form (JMS props / Kafka record / Redis stream fields).
 */
package pro.mir0n.esquire.messaging.transport;

import java.util.Map;

/**
 * Transport-neutral message moved by an {@link ITransportProvider}. {@code headers} is the property-bag
 * envelope (identity / routing fields and, when present, the body carried as a Text field per the codec);
 * {@code key} is an optional routing / partition key (e.g. the entity id, so a transport that partitions
 * keeps per-key order). The provider decides how the bag maps onto its wire format.
 */
public final class TransportMessage {

    private final Map<String, Object> headers;
    private final String key;

    public TransportMessage(Map<String, Object> headers, String key) {
        this.headers = headers;
        this.key     = key;
    }

    public Map<String, Object> headers() {
        return headers;
    }

    /** Optional routing / partition key; may be {@code null}. */
    public String key() {
        return key;
    }
}
