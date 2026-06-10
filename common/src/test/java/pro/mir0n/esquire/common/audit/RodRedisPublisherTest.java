/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/08/2026 mir0n  created: the (d) Redis publisher XADDs the event to the stream (key + body field, null
 *                   fields omitted) and swallows a Redis failure (best-effort, never thrown back to the feed).
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RodRedisPublisherTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void xaddsEventWithStreamKeyAndOmitsNullFields() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(ops);
        when(ops.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));

        RodRedisPublisher pub = new RodRedisPublisher(redis, EsqMsgConstants.STREAM_ROD_AUDIT, 0, new ObjectMapper());
        // subId is null -> must be omitted (a Redis stream field cannot be null)
        pub.accept(new RodEvent(RodEvent.Op.UPDATE, 50, "777", null, 1L, "c1", "r1", "u1", Map.of("name", "ACC")));

        ArgumentCaptor<MapRecord> cap = ArgumentCaptor.forClass(MapRecord.class);
        verify(ops).add(cap.capture());
        MapRecord<String, String, String> rec = cap.getValue();
        assertThat(rec.getStream()).isEqualTo(EsqMsgConstants.STREAM_ROD_AUDIT);
        Map<String, String> fields = rec.getValue();
        assertThat(fields).containsEntry(EsqMsgConstants.FIELD_ENTITY_ID, "777");
        assertThat(fields).containsKey(EsqMsgConstants.FIELD_TEXT);            // the body JSON
        assertThat(fields).doesNotContainKey(EsqMsgConstants.FIELD_SUB_ID);    // null omitted
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void redisFailureIsSwallowed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(ops);
        when(ops.add(any(MapRecord.class))).thenThrow(new RuntimeException("redis down"));

        RodRedisPublisher pub = new RodRedisPublisher(redis, EsqMsgConstants.STREAM_ROD_AUDIT, 0, new ObjectMapper());
        // best-effort: a Redis failure must NOT be thrown back into the single feed worker
        pub.accept(new RodEvent(RodEvent.Op.CREATE, 20, "1", null, 1L, "c", "r", "u", Map.of()));

        verify(ops).add(any(MapRecord.class));
    }
}
