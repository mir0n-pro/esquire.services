/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
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
        RodEvent in = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, null, 1717000000123L,
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
    void traceparentRidesTheWireOnAppEventsAndIsNullWhenAbsent() {
        RodEvent in = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, null, 1717000000123L,
                "0af7651916cd43dd8448eb211c80319c", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, Map.of())
                .withTraceparent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        RodEvent out = RodEventCodec.fromProps(RodEventCodec.toProps(in, om,
                new BusIdentity("audit-bus", "audit", null)), om);
        assertThat(out.traceparent()).isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        // no traceparent set -> the wire carries none, and the decoded event has none.
        RodEvent bare = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, null, 1L,
                "crl-x", "req-x", "uid-x", null, BusConstants.MSG_TYPE_AUDIT, Map.of());
        Map<String, Object> props = RodEventCodec.toProps(bare, om, new BusIdentity("audit-bus", "audit", null));
        assertThat(props).doesNotContainKey(BusConstants.FIELD_TRACEPARENT);
        assertThat(RodEventCodec.fromProps(props, om).traceparent()).isNull();
    }

    @Test
    void traceparentRidesTheWireOnSessionEventsForTheRrAliveRoundTrip() {
        // a TRACED RR liveness message (TestRequest) must carry its traceparent on the wire so the SERVER's
        // HeartBeat reply -- and the CLIENT's receipt -- nest into the same round-trip trace.
        String tp = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        RodEvent testReq = RodEvent.testRequest("0af7651916cd43dd8448eb211c80319c", "client.0").withTraceparent(tp);

        Map<String, Object> props = RodEventCodec.toProps(testReq, om, new BusIdentity("esquire.rr", "rr", "client.0"));
        assertThat(props).containsEntry(BusConstants.FIELD_TRACEPARENT, tp);
        RodEvent out = RodEventCodec.fromProps(props, om);
        assertThat(out.isSession()).isTrue();
        assertThat(out.traceparent()).isEqualTo(tp);

        // an UNtraced heartbeat (the default) carries no traceparent on the wire.
        RodEvent bareHb = RodEvent.heartbeat("crl-x", null, "server.0");
        Map<String, Object> hbProps = RodEventCodec.toProps(bareHb, om, new BusIdentity("esquire.rr", "rr", "server.0"));
        assertThat(hbProps).doesNotContainKey(BusConstants.FIELD_TRACEPARENT);
    }

    @Test
    void deleteRoundTripHasEmptyBodyAndKeepsSubId() {
        RodEvent in = new RodEvent(RodEvent.Op.DELETE, 988, "200", "777", null, 5L,
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
    void aMalformedBodyIsRejectedWithAClearError() {
        // A message whose body is not valid JSON must fail decode with a CLEAR, identifiable error -- the
        // consumer's catch-and-log (e.g. the audit keep) relies on a malformed message failing cleanly here,
        // not silently half-applying or throwing an opaque parser exception.
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE);
        p.put(BusConstants.FIELD_ENTITY_ID, "300");
        p.put(BusConstants.FIELD_TEXT, "{ this is : not json ");   // malformed body

        assertThatThrownBy(() -> RodEventCodec.fromProps(p, om))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserialize body");
    }

    @Test
    void identityAndMsgTypeRideTheEnvelope() {
        // the transport identity (bus-id / slot-id / rod-id) + the event's msg-type ride the envelope
        RodEvent audit = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, null, 1L, "crl", "req", "uid",
                null, BusConstants.MSG_TYPE_AUDIT, Map.of());
        Map<String, Object> auditProps = RodEventCodec.toProps(audit, om,
                new BusIdentity("audit-bus", "eny-rod", "ctrl-7"));
        assertThat(auditProps).containsEntry(BusConstants.FIELD_BUS_ID, "audit-bus");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_SLOT_ID, "eny-rod");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_ROD_ID, "ctrl-7");
        assertThat(auditProps).containsEntry(BusConstants.FIELD_MSG_TYPE, BusConstants.MSG_TYPE_AUDIT);

        // a DIFFERENT bus + msg-type rides the SAME codec unchanged; a blank rod-id is omitted
        RodEvent bcast = new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, null, 1L, "crl", "req", "uid",
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

    // --- v1.2.12: the (sub)entity change number on the wire (ChangeNo, tag 50015) ---

    @Test
    void changeNoRoundTrips() {
        RodEvent in = new RodEvent(RodEvent.Op.UPDATE, 34, "100", null, 7L, 1717000000123L,
                "crl-1", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, Map.of());

        Map<String, Object> props = RodEventCodec.toProps(in, om, new BusIdentity("audit-bus", "audit", null));
        assertThat(props).containsEntry(BusConstants.FIELD_CHANGE_NO, 7L);

        assertThat(RodEventCodec.fromProps(props, om).changeNo()).isEqualTo(7L);
    }

    @Test
    void changeNoAbsentStaysNullAndIsNotWrittenToTheWire() {
        // A producer with no row behind the event supplies none. Absent must NOT decode as 0: a receiver
        // guarding on "greater wins" would otherwise treat an unknown number as the lowest possible value.
        RodEvent in = new RodEvent(RodEvent.Op.UPDATE, 34, "100", null, null, 1717000000123L,
                "crl-1", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, Map.of());

        Map<String, Object> props = RodEventCodec.toProps(in, om, new BusIdentity("audit-bus", "audit", null));
        assertThat(props).doesNotContainKey(BusConstants.FIELD_CHANGE_NO);

        assertThat(RodEventCodec.fromProps(props, om).changeNo()).isNull();
    }

    @Test
    void changeNoSurvivesAsAStringOnTheWire() {
        // ActiveMQ carries every non-Integer property as a String (jms/Utils.setProps), so the decoder must
        // accept the string form and still yield the number.
        Map<String, Object> props = RodEventCodec.toProps(
                new RodEvent(RodEvent.Op.UPDATE, 34, "100", null, 42L, 1717000000123L,
                        "crl-1", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, Map.of()),
                om, new BusIdentity("audit-bus", "audit", null));
        props.put(BusConstants.FIELD_CHANGE_NO, "42");          // as ActiveMQ would hand it back

        assertThat(RodEventCodec.fromProps(props, om).changeNo()).isEqualTo(42L);
    }

    @Test
    void changeNoIsPreservedByTheEngineStamps() {
        // applMsgId and traceparent are stamped by the engine AFTER the producer set the change number in the
        // constructor -- both withX() copiers must carry it through, or the number is lost on the send path.
        RodEvent e = new RodEvent(RodEvent.Op.UPDATE, 34, "100", null, 5L, 1717000000123L,
                "crl-1", "req-1", "uid-9", null, BusConstants.MSG_TYPE_AUDIT, Map.of())
                .withApplMsgId("msg-1")
                .withTraceparent("00-aaaa-bbbb-01");

        assertThat(e.changeNo()).isEqualTo(5L);
        assertThat(e.applMsgId()).isEqualTo("msg-1");
        assertThat(e.traceparent()).isEqualTo("00-aaaa-bbbb-01");
    }
}
