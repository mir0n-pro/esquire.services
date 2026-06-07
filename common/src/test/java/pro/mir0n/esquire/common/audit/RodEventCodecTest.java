/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: round-trip tests for the x-Rod option (c) wire codec.
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RodEventCodecTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void updateRoundTripPreservesAllFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "ACC-1");
        body.put("ccy", "USD");
        body.put("status", "O");
        body.put("etPk", 50);
        RodEvent in = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, 1717000000123L,
                "crl-1", "req-1", "uid-9", body);

        RodEvent out = RodEventCodec.fromProps(RodEventCodec.toProps(in, om), om);

        assertThat(out.op()).isEqualTo(RodEvent.Op.UPDATE);
        assertThat(out.kind()).isEqualTo(50);
        assertThat(out.entityId()).isEqualTo("100");
        assertThat(out.subId()).isNull();
        assertThat(out.actionTime()).isEqualTo(1717000000123L);
        assertThat(out.correlationId()).isEqualTo("crl-1");
        assertThat(out.requestId()).isEqualTo("req-1");
        assertThat(out.uid()).isEqualTo("uid-9");
        assertThat(out.body()).isEqualTo(body);
    }

    @Test
    void deleteRoundTripHasEmptyBodyAndKeepsSubId() {
        RodEvent in = new RodEvent(RodEvent.Op.DELETE, 988, "200", "777", 5L,
                "crl-2", "req-2", null, Map.of());

        RodEvent out = RodEventCodec.fromProps(RodEventCodec.toProps(in, om), om);

        assertThat(out.op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(out.kind()).isEqualTo(988);
        assertThat(out.entityId()).isEqualTo("200");
        assertThat(out.subId()).isEqualTo("777");
        assertThat(out.uid()).isNull();
        assertThat(out.body()).isEmpty();
    }

    @Test
    void tolerantOfStringTypedKindAndActionTime() {
        // simulates values arriving as JMS string properties
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(EsqMsgConstants.FIELD_EVENT_TYPE, EsqMsgConstants.EVENT_CREATE);
        p.put(EsqMsgConstants.FIELD_ENTITY_KIND, "34");
        p.put(EsqMsgConstants.FIELD_ENTITY_ID, "300");
        p.put(EsqMsgConstants.FIELD_ACTION_TIME, "42");
        p.put(EsqMsgConstants.FIELD_TEXT, "{\"name\":\"X\"}");

        RodEvent out = RodEventCodec.fromProps(p, om);

        assertThat(out.op()).isEqualTo(RodEvent.Op.CREATE);
        assertThat(out.kind()).isEqualTo(34);
        assertThat(out.actionTime()).isEqualTo(42L);
        assertThat(out.body()).containsEntry("name", "X");
    }
}
