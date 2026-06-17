/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the publish-side (xy-rod) TransportSettings -- adds the async publisher pool size
 *                   (0 = the caller's single feed worker) for openPublisher.
 */
package pro.mir0n.esquire.messaging.transport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** Publish-side (xy-rod) settings for {@link ITransportProvider#openPublisher}. */
public final class PublishSettings extends TransportSettings {

    private final int poolSize;  // async publisher threads; 0 = caller's single feed worker

    public PublishSettings(ObjectMapper objectMapper, String endpoint, String clientId, boolean topic,
                           BusIdentity identity, Map<String, String> params, int poolSize) {
        super(objectMapper, endpoint, clientId, topic, identity, params);
        this.poolSize = poolSize;
    }

    public int poolSize() {
        return poolSize;
    }
}
