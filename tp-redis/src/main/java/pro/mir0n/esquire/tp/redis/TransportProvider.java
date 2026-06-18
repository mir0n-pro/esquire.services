/*
 *  Esquire frameworks (tm)
 *  tp-redis -- transport provider (Redis stream)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/12/2026 mir0n  created (as RedisTransportProvider): the Redis ITransportProvider (option d). openPublisher
 *                   XADDs each TransportMessage's header bag to the destination stream (the stream IS the
 *                   append-only log), optionally approximate-trimmed to max-len. Producer-only.
 * 06/13/2026 mir0n  class-name-driven SPI: renamed to the conventional pro.mir0n.esquire.tp.redis.
 *                   TransportProvider; producer-only declared via supportsConsume()=false (no vendor constant);
 *                   builds its OWN Lettuce connection from settings.endpoint(); the redis-only max-len comes
 *                   from the provider's param group (transport.redis.max-len), read via settings.params().
 * 06/17/2026 mir0n  openPublisher returns a TransportPublisher (close() destroys the LettuceConnectionFactory)
 */
package pro.mir0n.esquire.tp.redis;

import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Redis-stream implementation of the transport-provider SPI. Producer-only. */
public final class TransportProvider implements ITransportProvider {

    /** Redis-only OPERATION param (transport.params.max-len): approximate stream MAXLEN; 0 = unbounded. It is an
     *  XADD option, NOT a connection param, so it is excluded from the connection URI below. */
    public static final String PARAM_MAX_LEN = "max-len";

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.redis.TransportProvider");

    public TransportProvider() {
    }

    @Override
    public boolean supportsConsume() {
        return false;   // the stream IS the append-only log (read via XRANGE); there is no xxRod consume leg
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        long maxLen = s.paramLong(PARAM_MAX_LEN, 0L);
        StringRedisTemplate redis = buildTemplate(s.endpoint(), s.params());
        devLog.info("tp-redis: publisher opened on stream {} (endpoint={}, maxLen={})", destination, s.endpoint(), maxLen);

        // close() disposes the Lettuce connection factory built above (a DisposableBean).
        AutoCloseable closer = (redis.getConnectionFactory() instanceof DisposableBean db) ? db::destroy : () -> { };
        return TransportPublisher.of(msg -> {
            try {
                Map<String, Object> props = new LinkedHashMap<>(msg.headers());
                String applMsgId = UUID.randomUUID().toString();
                props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,  applMsgId);
                props.put(EsqMsgConstants.FIELD_SENDING_TIME, Instant.now().toString());

                Map<String, String> fields = new LinkedHashMap<>();
                props.forEach((k, v) -> {
                    if (v != null) {
                        fields.put(k, v.toString());   // stream fields are strings; null fields are omitted
                    }
                });

                MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(destination);
                if (maxLen > 0) {
                    redis.opsForStream().add(record, XAddOptions.maxlen(maxLen).approximateTrimming(true));
                } else {
                    redis.opsForStream().add(record);
                }
            } catch (Exception ex) {
                devLog.error("tp-redis: XADD failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }, closer);
    }

    @Override
    public AutoCloseable openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        throw new UnsupportedOperationException(
                "tp-redis is producer-only: the stream is the append-only log (read with XRANGE); no consumer");
    }

    /** Build a started StringRedisTemplate over a Lettuce connection to {@code endpoint}. Every leg {@code params}
     *  entry EXCEPT {@code max-len} (an XADD op) is appended to the {@code redis://} URI as a connection option,
     *  so Lettuce applies it verbatim (database / timeout / ...); the endpoint authority carries host/port/password.
     *  The audit stream owns this connection -- it is not the app's shared redis. */
    private static StringRedisTemplate buildTemplate(String endpoint, Map<String, String> params) {
        StringBuilder uri = new StringBuilder(
                endpoint.startsWith("redis://") || endpoint.startsWith("rediss://") ? endpoint : "redis://" + endpoint);
        boolean q = uri.indexOf("?") >= 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (PARAM_MAX_LEN.equals(e.getKey())) {
                continue;   // XADD option, not a connection param
            }
            uri.append(q ? '&' : '?').append(e.getKey()).append('=').append(e.getValue());
            q = true;
        }
        RedisURI r = RedisURI.create(uri.toString());
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(r.getHost(), r.getPort());
        cfg.setDatabase(r.getDatabase());
        if (r.getPassword() != null && r.getPassword().length > 0) {
            cfg.setPassword(RedisPassword.of(new String(r.getPassword())));
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder().commandTimeout(r.getTimeout()).build();
        LettuceConnectionFactory cf = new LettuceConnectionFactory(cfg, client);
        cf.afterPropertiesSet();
        cf.start();
        StringRedisTemplate template = new StringRedisTemplate(cf);
        template.afterPropertiesSet();
        return template;
    }
}
