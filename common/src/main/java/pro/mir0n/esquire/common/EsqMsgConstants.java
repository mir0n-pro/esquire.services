/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: Phase-1 ActiveMQ messaging protocol constants (FIX-JSON)
 * 03/20/2026 mir0n  SERVICE_ID_ENTITY_BROADCAST = "entity-update-broadcast" added
 * 03/21/2026 mir0n  KC sync constants added: QUEUE_KC_*, MSG_TYPE_REQUEST/RESPONSE/REJECT,
 *                   FIELD_TEST_REQ_ID, FIELD_ERROR; ENTITY_KIND_ACCESS_PROFILE removed (dup of EsqConstants.KIND_ACCESS_PROFILE)
 * 03/26/2026 mir0n  TEXT_* JSON field name constants (TEXT_ID/KIND/NAME/DESC/STATUS/DELETED/PARENT_ID/PATH/CCY);
 *                   MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING); FLAG_OPEN="O"; CCY_DEFAULT="USD"
 */
package pro.mir0n.esquire.common;

/**
 * Protocol constants for the esquire.entity.broadcast JMS topic.
 *
 * All 14 canonical fields are transmitted as JMS message properties (no message body).
 * Text carries a JSON-serialized entity state snapshot as a string property.
 * Fixed phase-1 values must not change without protocol review.
 */
public class EsqMsgConstants {
    private EsqMsgConstants() {}

    // --- Destinations ---
    public static final String TOPIC_ENTITY_BROADCAST = "esquire.entity.broadcast";
    public static final String QUEUE_KC_REQUEST        = "esquire.kc.request";
    public static final String QUEUE_KC_RESPONSE       = "esquire.kc.response";

    // --- Canonical FIX-JSON field names (JMS property name = JSON body field name) ---

    // Required on every message regardless of MsgType:
    //   MsgType  — dictionary key; drives validation of all other fields.
    //   ApplMsgID — unique message identity; required for dedup and tracing.
    //   BusID / ServiceID — mandatory routing envelope fields.
    public static final String FIELD_MSG_TYPE          = "MsgType";           // FIX 35
    public static final String FIELD_APPL_MSG_ID       = "ApplMsgID";         // FIX 1181
    public static final String FIELD_BUS_ID            = "BusID";             // FIX 50002
    public static final String FIELD_SERVICE_ID        = "ServiceID";         // FIX 50003

    // Conditionally required — presence depends on MsgType (see message dictionary, TBD):
    public static final String FIELD_SENDING_TIME      = "SendingTime";       // FIX 52
    public static final String FIELD_TEXT              = "Text";              // FIX 58
    public static final String FIELD_MESSAGE_ENCODING  = "MessageEncoding";   // FIX 347
    public static final String FIELD_SCHEMA_VERSION    = "SchemaVersion";     // FIX 50001
    public static final String FIELD_CTRL_ID           = "CtrlID";            // FIX 50004
    public static final String FIELD_EVENT_TYPE        = "EventType";         // FIX 50005
    public static final String FIELD_ENTITY_KIND       = "EntityKind";        // FIX 50006
    public static final String FIELD_ENTITY_ID         = "EntityID";          // FIX 50007
    public static final String FIELD_REQUEST_ID        = "RequestID";         // FIX 50008
    public static final String FIELD_CORRELATION_ID    = "CorrelationID";     // FIX 50009
    public static final String FIELD_TEST_REQ_ID       = "TestReqID";         // FIX 112make
    public static final String FIELD_ERROR             = "Error";             // FIX 50010


    // --- Fixed phase-1 values ---
    public static final int    SCHEMA_VERSION          = 1;
    public static final String BUS_ID_ENTITY           = "esquire.entity";
    public static final String SERVICE_ID_ENTITY_BROADCAST = "entity-update-broadcast";
    public static final String MSG_TYPE_ENTITY_BROADCASTS  = "UE";
    public static final String MSG_TYPE_REQUEST         = "URQ";
    public static final String MSG_TYPE_RESPONSE        = "URS";
    public static final String MSG_TYPE_REJECT          = "URR";
    public static final String MSG_ENCODING_JSON        = "JSON";

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

