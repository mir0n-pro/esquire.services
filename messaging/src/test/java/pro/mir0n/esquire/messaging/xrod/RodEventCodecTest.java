/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: round-trip tests for the x-Rod option (c) wire codec.
 * 06/13/2026 mir0n  +identity test: slot-id / ctrl-id are config-driven (toProps args).
 * 06/14/2026 mir0n  identity is a BusIdentity (bus-id / slot-id / rod-id); the msg-type rides ON the event
 *                   (e.msgType()) -- the test exercises both buses (RDA / UE) through the codec.
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                "crl-1", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, body);

        RodEvent out = RodEventCodec.fromProps(RodEventCodec.toProps(in, om,
                new BusIdentity("audit-bus", "audit", null)), om);

        assertThat(out.op()).isEqualTo(RodEvent.Op.UPDATE);
        assertThat(out.kind()).isEqualTo(50);
        assertThat(out.entityId()).isEqualTo("100");
        assertThat(out.subId()).isNull();
        assertThat(out.actionTime()).isEqualTo(1717000000123L);
        assertThat(out.correlationId()).isEqualTo("crl-1");
        assertThat(out.requestId()).isEqualTo("req-1");
        assertThat(out.uid()).isEqualTo("uid-9");
        assertThat(out.msgType()).isEqualTo(BusConstants.MSG_TYPE_AUDIT);
        assertThat(out.body()).isEqualTo(body);
    }

    @Test
    void deleteRoundTripHasEmptyBodyAndKeepsSubId() {
        RodEvent in = new RodEvent(RodEvent.Op.DELETE, 988, "200", "777", 5L,
                "crl-2", "req-2", null, null, BusConstants.MSG_TYPE_AUDIT, Map.of());

        RodEvent out = RodEventCodec.fromProps(RodEventCodec.toProps(in, om,
                new BusIdentity("audit-bus", "audit", null)), om);

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
        p.put(BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE);
        p.put(BusConstants.FIELD_ENTITY_KIND, "34");
        p.put(BusConstants.FIELD_ENTITY_ID, "300");
        p.put(BusConstants.FIELD_ACTION_TIME, "42");
        p.put(BusConstants.FIELD_TEXT, "{\"name\":\"X\"}");

        RodEvent out = RodEventCodec.fromProps(p, om);

        assertThat(out.op()).isEqualTo(RodEvent.Op.CREATE);
        assertThat(out.kind()).isEqualTo(34);
        assertThat(out.actionTime()).isEqualTo(42L);
        assertThat(out.body()).containsEntry("name", "X");
    }

    @Test
    void aBadNumericFieldFallsBackInsteadOfDroppingTheWholeEvent() {
        // a malformed kind / actionTime must NOT abort the decode -- the event survives with the field defaulted
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE);
        p.put(BusConstants.FIELD_ENTITY_KIND, "not-a-number");
        p.put(BusConstants.FIELD_ENTITY_ID, "300");
        p.put(BusConstants.FIELD_ACTION_TIME, "bad");
        p.put(BusConstants.FIELD_TEXT, "{\"name\":\"X\"}");

        RodEvent out = RodEventCodec.fromProps(p, om);

        assertThat(out.kind()).isZero();                 // bad field -> default, not a thrown decode
        assertThat(out.actionTime()).isZero();
        assertThat(out.entityId()).isEqualTo("300");     // the rest of the event survives
        assertThat(out.body()).containsEntry("name", "X");
    }

    @Test
    void aMismatchedSchemaVersionIsRejected() {
        // a version that is present but differs from this codec's is rejected (an unknown schema is untrustworthy)
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(BusConstants.FIELD_SCHEMA_VERSION, BusConstants.SCHEMA_VERSION + 1);
        p.put(BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE);
        p.put(BusConstants.FIELD_ENTITY_ID, "300");

        assertThatThrownBy(() -> RodEventCodec.fromProps(p, om))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema version");
    }

    @Test
    void identityAndMsgTypeRideTheEnvelope() {
        // the transport identity (bus-id / slot-id / rod-id) + the event's msg-type ride the envelope
        RodEvent audit = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, 1L, "crl", "req", "uid",
                null, BusConstants.MSG_TYPE_AUDIT, Map.of());
        Map<String, Object> auditProps = RodEventCodec.toProps(audit, om,
                new BusIdentity("audit-bus", "eny-rod", "ctrl-7"));
        assertThat(auditProps).containsEntry(BusConstants.FIELD_BUS_ID, "audit-bus");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_SLOT_ID, "eny-rod");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_ROD_ID, "ctrl-7");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_AUDIT);

        // a DIFFERENT bus + msg-type rides the SAME codec unchanged; a blank rod-id is omitted
        RodEvent bcast = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, 1L, "crl", "req", "uid",
                null, BusConstants.MSG_TYPE_ENTITY_BROADCASTS, Map.of());
        Map<String, Object> bcastProps = RodEventCodec.toProps(bcast, om,
                new BusIdentity("esquire.entity", "entity", ""));
        assertThat(bcastProps).containsEntry(BusConstants.FIELD_BUS_ID, "esquire.entity");
        assertThat(bcastProps).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_ENTITY_BROADCASTS);
        assertThat(bcastProps).doesNotContainKey(BusConstants.FIELD_ROD_ID);
    }

    // ----------------------------------------------------------------- session (alive) messages

    @Test
    void unsolicitedHeartbeatRoundTrip_reducedFieldSet() {
        RodEvent hb = RodEvent.heartbeat("corr-hb", null, null);   // unsolicited: no requestId, leg's own rod-id
        Map<String, Object> props = RodEventCodec.toProps(hb, om, new BusIdentity("esquire.entity", "entity", "biz.0"));
        // the reduced set: identity + correlation + msg-type + encoding + text; NO CRUD fields, NO header TestReqID
        assertThat(props).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_HEARTBEAT);
        assertThat(props).containsEntry(BusConstants.FIELD_CORRELATION_ID, "corr-hb");
        assertThat(props).doesNotContainKey(BusConstants.FIELD_REQUEST_ID);   // omitted on an unsolicited HB
        assertThat(props).doesNotContainKey(BusConstants.FIELD_EVENT_TYPE);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_ENTITY_KIND);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_ENTITY_ID);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_SUB_ID);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_ACTION_TIME);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_UID);
        assertThat(props).doesNotContainKey(BusConstants.FIELD_TEST_REQ_ID);  // TestReqID rides in Text, not a header

        RodEvent out = RodEventCodec.fromProps(props, om);
        assertThat(out.isSession()).isTrue();
        assertThat(out.msgType()).isEqualTo(BusConstants.MSG_TYPE_HEARTBEAT);
        assertThat(out.correlationId()).isEqualTo("corr-hb");
        assertThat(out.requestId()).isNull();
        assertThat(out.op()).isNull();
        assertThat(out.body()).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_HEARTBEAT);
    }

    @Test
    void testRequestRoundTrip_carriesRequestIdAndTestReqIdInBody() {
        RodEvent tr = RodEvent.testRequest("corr-tr", null);
        Map<String, Object> props = RodEventCodec.toProps(tr, om, new BusIdentity("esquire.kc", "kc", "eny.0"));
        assertThat(props).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_TEST_REQUEST);
        assertThat(props).containsEntry(BusConstants.FIELD_REQUEST_ID, "corr-tr");   // = correlationId
        assertThat(props).containsEntry(BusConstants.FIELD_ROD_ID, "eny.0");         // the leg's rod-id rides

        RodEvent out = RodEventCodec.fromProps(props, om);
        assertThat(out.msgType()).isEqualTo(BusConstants.MSG_TYPE_TEST_REQUEST);
        assertThat(out.requestId()).isEqualTo("corr-tr");
        assertThat(out.rodId()).isEqualTo("eny.0");
        assertThat(out.body()).containsEntry(BusConstants.FIELD_TEST_REQ_ID, "corr-tr");   // TestReqID in the body
    }

    @Test
    void heartbeatResponse_echoesRequesterRouting() {
        // a SERVER's HeartBeat reply echoes the requester's rod-id + correlation/request so it routes back
        RodEvent reply = RodEvent.heartbeat("corr-tr", "corr-tr", "client.3");
        Map<String, Object> props = RodEventCodec.toProps(reply, om, new BusIdentity("esquire.kc", "kc", "kcmaster.0"));
        assertThat(props).containsEntry(BusConstants.FIELD_ROD_ID, "client.3");     // the requester's rod-id, NOT the leg's
        assertThat(props).containsEntry(BusConstants.FIELD_REQUEST_ID, "corr-tr");

        RodEvent out = RodEventCodec.fromProps(props, om);
        assertThat(out.msgType()).isEqualTo(BusConstants.MSG_TYPE_HEARTBEAT);
        assertThat(out.rodId()).isEqualTo("client.3");
        assertThat(out.requestId()).isEqualTo("corr-tr");
    }
}
