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
 * 06/27/2026 mir0n  PARAM_NO_LOCAL ("noLocal") added -- the transport-leg vendor param key for the shared-connection
 *                   own-exclusion (broadcast only): a receive leg sharing the publisher's connection drops THIS
 *                   connection's own publications
 * 07/09/2026 mir0n  v1.2.11 -- FIELD_TRACEPARENT ("TraceParent", FIX 50014) added
 * 08/11/2026 mir0n  v1.2.12 -- FIELD_CHANGE_NO added (ChangeNo, FIX 50015), the per-row change number
 *                   supplied by the producer; the declared exception is stated at the field: C/U/D carry
 *                   the entity row's number, X the path row's, never comparable
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
    // W3C traceparent riding the bus hop (v1.2.11 O2/T3): carries the PRODUCER's parent span id so a
    // consumer span nests under it. The trace id half is authoritative from FIELD_CORRELATION_ID, not
    // this field. Absent on session (heartbeat / test-request) messages.
    public static final String FIELD_TRACEPARENT       = "TraceParent";        // FIX 50014 (W3C trace context)
    // The (sub)entity CHANGE NUMBER (v1.2.12). Wire identity is EntityID + EntityKind + (optional) SubID
    // plus this number; a GREATER number is fresher. Supplied by the PRODUCER (it just raised and wrote it),
    // never stamped by the engine. Absent on session messages, and on any producer that has no row behind
    // the event -- so a consumer must treat "missing" as "unknown", not as zero.
    //
    // THE ONE EXCEPTION TO THE UNIFORM RULE -- read this before comparing two numbers.
    // The uniform rule is: this field is the ENTITY row's change number. A PATH message (op X,
    // EVENT_UPDATE_PATH) breaks it: EntityKind still names the entity kind, and EntityID still names the
    // entity, but the number is the PATH row's (ESQ_ENTITY_PATH.EP_CHANGE_NO), NOT the entity row's.
    //
    // It has to be that way. A move rewrites every DESCENDANT's path row while leaving those descendants'
    // entity rows untouched, so a descendant's move has no entity number to report -- the path number is
    // the only one that moved. Both are per-entity counters (each unique within its entity id), but they
    // are SEPARATE counters and are never comparable with each other.
    //
    // So a receiver must guard like against like: a path event against its stored PATH number, an entity
    // event against its stored ENTITY number. Comparing across the two drops path updates and leaves a
    // moved subtree half-repathed. There is no rule without an exception; this is the one.
    public static final String FIELD_CHANGE_NO         = "ChangeNo";           // FIX 50015 (per-row change number)


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

    // --- Transport-leg vendor params (read by a transport provider off transport.params.*; named here so the
    //     x-rod and the provider agree on the key) ---
    //   noLocal -- a receive leg that shares its connection with the rod's own publisher leg does NOT get this
    //   connection's own publications (the JMS noLocal semantic). A rod with two separate connections (no shared
    //   connection) excludes its own in code instead. Broadcast (XRod) only; R&R never uses it.
    public static final String PARAM_NO_LOCAL          = "noLocal";

    // --- EventType vocabulary ---
    public static final String EVENT_CREATE            = "I";
    public static final String EVENT_UPDATE            = "U";
    public static final String EVENT_DELETE            = "D";
    public static final String EVENT_UPDATE_PATH       = "X";

}
