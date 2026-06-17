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
 */
package pro.mir0n.esquire.messaging.transport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Base of the transport settings hierarchy: the bus connection (endpoint), the connection client-id, the
 * destination kind (queue vs topic), the envelope {@link BusIdentity}, and the provider's own {@code params}
 * group. The provider builds its client from {@link #endpoint()} and reads its vendor knobs from
 * {@link #params()} by key.
 */
public class TransportSettings {

    private final ObjectMapper objectMapper;
    private final String endpoint;        // broker-url | bootstrap-servers | redis host:port; provider builds its client
    private final String clientId;        // transport connection client id (optional)
    private final boolean topic;          // destination kind: false = queue (point-to-point), true = topic (pub/sub)
    private final BusIdentity identity;   // envelope identity (bus-id / msg-type / slot-id / ctrl-id)
    private final Map<String, String> params;  // the provider's own group (transport.<provider>.*); never null

    public TransportSettings(ObjectMapper objectMapper, String endpoint, String clientId, boolean topic,
                             BusIdentity identity, Map<String, String> params) {
        this.objectMapper = objectMapper;
        this.endpoint     = endpoint;
        this.clientId     = clientId;
        this.topic        = topic;
        this.identity     = identity;
        this.params       = params != null ? params : Map.of();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public String endpoint() {
        return endpoint;
    }

    public String clientId() {
        return clientId;
    }

    /** Destination kind: {@code true} = topic (pub/sub, every consumer gets every message); {@code false} = queue. */
    public boolean topic() {
        return topic;
    }

    /** The bus envelope identity (bus-id / msg-type / slot-id / ctrl-id). */
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
