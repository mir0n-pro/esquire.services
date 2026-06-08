/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/08/2026 mir0n  created: the x-Rod option (d) producer dispatcher -- a Consumer<RodEvent> that the
 *                   xy-Rod feed worker calls. It serializes the event via RodEventCodec and XADDs it to a
 *                   Redis Stream (the stream IS the append-only audit log; no consumer service). Direct
 *                   producer -> Redis, no broker. Best-effort: a Redis failure is logged and the event
 *                   dropped (same loss profile as the in-process / bus paths), never thrown back into the
 *                   single feed worker. Optional approximate MAXLEN caps the stream's growth.
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * XADDs each committed {@link RodEvent} to the audit Redis Stream (x-Rod option d). Wire-built once per
 * asset service and handed to {@code XYRod} as its dispatcher. The stream is the audit log itself -- there
 * is no consumer service; history is read with {@code XRANGE} (and consumer groups can fan out later).
 *
 * <p>The event is mapped to a stream record via {@link RodEventCodec#toProps} (header fields + the body as
 * one JSON {@code Text} field); null fields are dropped (a Redis stream field cannot be null). When
 * {@code maxLen > 0} the add uses approximate trimming so the stream stays bounded.
 */
public final class RodRedisPublisher implements Consumer<RodEvent> {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodRedisPublisher.class.getName());
    private static final Logger msgLog = LoggerFactory.getLogger("msg." + RodRedisPublisher.class.getName());

    private final StringRedisTemplate redis;
    private final String stream;
    private final long   maxLen;
    private final ObjectMapper om;

    /**
     * @param redis   the string-serialized Redis template (built by the service from its connection).
     * @param stream  the stream key to XADD to (see {@link EsqMsgConstants#STREAM_ROD_AUDIT}).
     * @param maxLen  approximate MAXLEN cap; <= 0 means no trimming (unbounded stream).
     * @param om      JSON mapper for the event body.
     */
    public RodRedisPublisher(StringRedisTemplate redis, String stream, long maxLen, ObjectMapper om) {
        this.redis  = redis;
        this.stream = stream;
        this.maxLen = maxLen;
        this.om     = om;
    }

    @Override
    public void accept(RodEvent e) {
        try {
            Map<String, Object> props = RodEventCodec.toProps(e, om);
            String applMsgId = UUID.randomUUID().toString();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,  applMsgId);
            props.put(EsqMsgConstants.FIELD_SENDING_TIME, Instant.now().toString());

            Map<String, String> fields = new LinkedHashMap<>();
            props.forEach((k, v) -> {
                if (v != null) {
                    fields.put(k, v.toString());   // stream fields are strings; null fields are omitted
                }
            });

            MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(stream);
            RecordId id = (maxLen > 0)
                    ? redis.opsForStream().add(record, XAddOptions.maxlen(maxLen).approximateTrimming(true))
                    : redis.opsForStream().add(record);

            msgLog.info("ROD | RDA | {} | {} | {} | {} | {} | {}",
                    applMsgId, props.get(EsqMsgConstants.FIELD_EVENT_TYPE), e.kind(), e.entityId(), e.subId(),
                    id != null ? id.getValue() : null);
        } catch (Exception ex) {
            devLog.error("rod-redis: XADD failed for kind={}, entityId={}, subId={}: {}",
                    e.kind(), e.entityId(), e.subId(), ex.getMessage(), ex);
        }
    }
}
