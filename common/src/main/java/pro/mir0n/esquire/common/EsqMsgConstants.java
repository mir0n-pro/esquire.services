/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: Phase-1 ActiveMQ messaging protocol constants (FIX-JSON)
 * 03/20/2026 mir0n  SERVICE_ID_ENTITY_BROADCAST = "entity-update-broadcast" added
 * 03/21/2026 mir0n  KC sync constants added: QUEUE_KC_*, MSG_TYPE_REQUEST/RESPONSE/REJECT,
 *                   FIELD_TEST_REQ_ID, FIELD_ERROR; ENTITY_KIND_ACCESS_PROFILE removed (dup of EsqConstants.KIND_ACCESS_PROFILE)
 * 03/26/2026 mir0n  TEXT_* JSON field name constants (TEXT_ID/KIND/NAME/DESC/STATUS/DELETED/PARENT_ID/PATH/CCY);
 *                   MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING); FLAG_OPEN="O"; CCY_DEFAULT="USD"
 * 06/06/2026 mir0n  x-Rod audit bus (option c): QUEUE_ROD_AUDIT, MSG_TYPE_ROD_AUDIT, BUS_ID_ROD,
 *                   SERVICE_ID_ROD_AUDIT, FIELD_SUB_ID / FIELD_UID / FIELD_ACTION_TIME added
 * 06/08/2026 mir0n  x-Rod audit option (d): STREAM_ROD_AUDIT (the Redis Stream key) added
 * 06/08/2026 mir0n  x-Rod audit option (c) over Kafka: TOPIC_ROD_AUDIT (the Kafka topic) added
 * 06/15/2026 mir0n  transport-agnostic x-Rod rework: QUEUE_/TOPIC_/STREAM_ROD_AUDIT collapsed to the one
 *                   logical ROD_AUDIT destination; FIELD_SERVICE_ID->FIELD_SLOT_ID (SlotID), FIELD_CTRL_ID->
 *                   FIELD_ROD_ID (RodID); MSG_TYPE_ROD_AUDIT->MSG_TYPE_AUDIT ("UA"); hardcoded bus/slot value
 *                   constants (BUS_ID_ROD/ENTITY, SERVICE_ID_*) removed -- replaced by logical bus KEYS
 *                   BUS_KEY_AUDIT/KC/ENTITY (the bus-id/slot-id VALUES are now config/topology)
 * 06/17/2026 mir0n  TOPIC_ENTITY_BROADCAST and ROD_AUDIT removed (dead destination constants -- destinations are
 *                   config/topology values now); class javadoc refreshed to the FIX-JSON shared-envelope description
 * 06/23/2026 mir0n  MSG_TYPE_HEARTBEAT "0" / MSG_TYPE_TEST_REQUEST "1" -- the x-rod alive-protocol session msg-types
 */
package pro.mir0n.esquire.common;

/**
 * FIX-JSON protocol constants shared across the messaging bus -- entity broadcast, KC request/response,
 * and audit. The field names and msg-type / event-type values define the wire envelope; the codec
 * (RodEventCodec) maps a RodEvent to and from it, so they are transport-agnostic (each provider carries the
 * envelope its own way -- a queue, a topic, a stream). Text carries a JSON entity-state snapshot.
 *
 * A service finds its bus by a logical KEY (BUS_KEY_*); the bus-id / slot-id / destination VALUES live in
 * config and topology, not here. The fixed values must not change without a protocol review.
 */
public class EsqMsgConstants {
    private EsqMsgConstants() {}

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
    // --- logical bus KEYS a service uses to look up its ref (esquire.<key>.messaging-bus -> {bus-id, slot-id}).
    //     The bus-id / slot-id VALUES are configurable (the topology + refs), NOT hardcoded here. ---
public static final String BUS_KEY_AUDIT            = "audit-bus";
    public static final String BUS_KEY_KC               = "kc-bus";
    public static final String BUS_KEY_ENTITY           = "entity-bus";

    // --- EventType vocabulary ---
    public static final String EVENT_CREATE            = "C";
    public static final String EVENT_UPDATE            = "U";
    public static final String EVENT_DELETE            = "D";
    public static final String EVENT_UPDATE_PATH       = "X";

    // --- Text JSON field names (entity state snapshot fields) ---
    public static final String TEXT_ID        = "id";
    public static final String TEXT_KIND      = "kind";
    public static final String TEXT_PARENT_ID = "parentId";
    public static final String TEXT_PATH      = "path";
    public static final String TEXT_NAME      = "name";
    public static final String TEXT_DESC      = "desc";
    public static final String TEXT_STATUS    = "status";
    public static final String TEXT_DELETED   = "deleted";
    public static final String TEXT_CCY       = "ccy";

    // --- Default field values ---
    public static final String FLAG_OPEN      = "O";
    public static final String CCY_DEFAULT    = "USD";

}

