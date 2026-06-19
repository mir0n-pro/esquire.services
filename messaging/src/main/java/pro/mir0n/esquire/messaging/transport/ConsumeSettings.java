/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the consume-side (xx-rod) TransportSettings -- adds listener concurrency and an
 *                   optional provider-specific message selector (null = consume everything) for openConsumer.
 * 06/17/2026 mir0n  the clientId constructor parameter removed
 */
package pro.mir0n.esquire.messaging.transport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** Consume-side (xx-rod) settings for {@link ITransportProvider#openConsumer}. */
public final class ConsumeSettings extends TransportSettings {

    private final int concurrency;  // consumer listener concurrency; <=0 = provider default
    private final String selector;  // optional message selector; null = none (provider-specific)

    public ConsumeSettings(ObjectMapper objectMapper, String endpoint, boolean topic,
                           BusIdentity identity, Map<String, String> params, int concurrency, String selector) {
        super(objectMapper, endpoint, topic, identity, params);
        this.concurrency = concurrency;
        this.selector    = selector;
    }

    public int concurrency() {
        return concurrency;
    }

    /** Optional consumer message selector (e.g. {@code RodID = 'enyman.0'}); null = consume everything. */
    public String selector() {
        return selector;
    }
}
