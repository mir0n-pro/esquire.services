/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the base of the transport-settings hierarchy (PublishSettings / ConsumeSettings) --
 *                   the JSON ObjectMapper, the bus connection (endpoint), the client-id, the destination kind
 *                   (queue vs topic), the BusIdentity envelope, and the provider's own generic params group (read
 *                   by key); paramLong / param convenience accessors.
 * 06/17/2026 mir0n  the constructor resolves the identity tokens in params (identity.expandTokens) -- one
 *                   driver-facing point; the clientId field / getter removed
 * 06/21/2026 mir0n  the topic field + topic() getter removed and the constructor drops the topic parameter
 *                   (queue-vs-topic is JMS-only -- now the ActiveMQ transport.params.pubSubDomain knob,
 *                   read by tp-activemq)
 */
package pro.mir0n.esquire.messaging.transport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Base of the transport settings hierarchy: the bus connection (endpoint), the envelope {@link BusIdentity},
 * and the provider's own {@code params} group. The provider builds its client from {@link #endpoint()} and
 * reads its vendor knobs from {@link #params()} by key (a vendor connection setting -- a client id, the JMS
 * {@code pubSubDomain} flag, etc. -- is set via {@code transport.params.*}, not a typed field here).
 */
public class TransportSettings {

    private final ObjectMapper objectMapper;
    private final String endpoint;        // broker-url | bootstrap-servers | redis host:port; provider builds its client
    private final BusIdentity identity;   // envelope identity (bus-id / slot-id / rod-id)
    private final Map<String, String> params;  // the provider's own group (transport.<provider>.*); never null

    public TransportSettings(ObjectMapper objectMapper, String endpoint,
                             BusIdentity identity, Map<String, String> params) {
        this.objectMapper = objectMapper;
        this.endpoint     = endpoint;
        this.identity     = identity;
        // resolve the ${rod-id}/${bus-id}/${slot-id} tokens against this leg's identity, so the driver receives
        // the real per-instance values (e.g. jms.clientID: ${rod-id}). One point: every settings object the
        // driver gets (single-node leg AND an R&R node) is expanded the same way, from the identity it carries.
        Map<String, String> p = params != null ? params : Map.of();
        this.params       = identity != null ? identity.expandTokens(p) : p;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public String endpoint() {
        return endpoint;
    }

    /** The bus envelope identity (bus-id / slot-id / rod-id). */
    public BusIdentity identity() {
        return identity;
    }

    /** The provider's own param group; read vendor knobs by key. Never null. */
    public Map<String, String> params() {
        return params;
    }

    /** Convenience: a param as a long, or {@code def} when absent / unparseable. */
    public long paramLong(String key, long def) {
        long ret = def;
        String v = params.get(key);
        if (v != null && !v.isBlank()) {
            try {
                ret = Long.parseLong(v.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return ret;
    }

    /** Convenience: a param as a string, or {@code def} when absent / blank. */
    public String param(String key, String def) {
        String v = params.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
