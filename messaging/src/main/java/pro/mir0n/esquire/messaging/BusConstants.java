/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/23/2026 mir0n  created: the messaging-bus FIX-JSON wire constants -- moved from common.EsqMsgConstants into
 *                   the messaging module (where the bus framework lives); the non-wire app constants
 *                   (BUS_KEY_* / TEXT_* / FLAG_OPEN / CCY_DEFAULT) split out to common.EsqConstants, so common no
 *                   longer carries any bus-wire definition.
 */
package pro.mir0n.esquire.messaging;

/**
 * FIX-JSON protocol constants shared across the messaging bus -- entity broadcast, KC request/response, and
 * audit. The field names and msg-type / event-type values define the wire envelope; the codec (RodEventCodec)
 * maps a RodEvent to and from it, so they are transport-agnostic (each provider carries the envelope its own
 * way -- a queue, a topic, a stream). Text carries a JSON entity-state snapshot.
 *
 * The fixed values must not change without a protocol review.
 */
public class BusConstants {
    private BusConstants() {}

    // --- Canonical FIX-JSON field names (JMS property name = JSON body field name) ---

    // Required on every message regardless of MsgType:
    //   MsgType  — dictionary key; drives validation of all other fields.
    //   ApplMsgID — unique message identity; required for dedup and tracing.
    //   BusID / SlotID — mandatory routing envelope fields.
    public static final String FIELD_MSG_TYPE          = "MsgType";           // FIX 35
    public static final String FIELD_APPL_MSG_ID       = "ApplMsgID";         // FIX 1181
    public static final String FIELD_BUS_ID            = "BusID";             // FIX 50002
    public static final String FIELD_SLOT_ID        = "SlotID";         // FIX 50003

    // Conditionally required — presence depends on MsgType (see message dictionary, TBD):
    public static final String FIELD_SENDING_TIME      = "SendingTime";       // FIX 52
    public static final String FIELD_TEXT              = "Text";              // FIX 58
    public static final String FIELD_MESSAGE_ENCODING  = "MessageEncoding";   // FIX 347
    public static final String FIELD_SCHEMA_VERSION    = "SchemaVersion";     // FIX 50001
    public static final String FIELD_ROD_ID            = "RodID";             // FIX 50004 (the originating instance id; reply-routing selector)
    public static final String FIELD_EVENT_TYPE        = "EventType";         // FIX 50005
    public static final String FIELD_ENTITY_KIND       = "EntityKind";        // FIX 50006
    public static final String FIELD_ENTITY_ID         = "EntityID";          // FIX 50007
    public static final String FIELD_REQUEST_ID        = "RequestID";         // FIX 50008
    public static final String FIELD_CORRELATION_ID    = "CorrelationID";     // FIX 50009
    public static final String FIELD_TEST_REQ_ID       = "TestReqID";         // FIX 112
    // x-Rod option (c) optional header fields (audit triple sub-row id + actor + commit time):
    public static final String FIELD_SUB_ID            = "SubID";             // FIX 50011
    public static final String FIELD_UID               = "Uid";               // FIX 50012
    public static final String FIELD_ACTION_TIME       = "ActionTime";        // FIX 50013 (epoch-ms at commit)


    // --- Fixed phase-1 values ---
    public static final int    SCHEMA_VERSION          = 1;
    public static final String MSG_TYPE_ENTITY_BROADCASTS  = "UE";
    public static final String MSG_TYPE_REQUEST         = "URQ";
    public static final String MSG_TYPE_RESPONSE        = "URS";
    public static final String MSG_TYPE_REJECT          = "URR";
    public static final String MSG_TYPE_AUDIT           = "UA";    // FIX custom msg-types start with 'U' -> UA = Update/Audit
    // x-rod session (alive protocol) msg-types -- FIX-canonical (0 = Heartbeat, 1 = TestRequest), distinct from
    // the U-prefixed application types above; handled internally by the x-rod session layer, never the app worker.
    public static final String MSG_TYPE_HEARTBEAT       = "0";
    public static final String MSG_TYPE_TEST_REQUEST    = "1";
    public static final String MSG_ENCODING_JSON        = "JSON";

    // --- EventType vocabulary ---
    public static final String EVENT_CREATE            = "C";
    public static final String EVENT_UPDATE            = "U";
    public static final String EVENT_DELETE            = "D";
    public static final String EVENT_UPDATE_PATH       = "X";

}
