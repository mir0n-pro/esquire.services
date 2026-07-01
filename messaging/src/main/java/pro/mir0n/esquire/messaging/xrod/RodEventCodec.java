/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the x-Rod wire codec -- the ONE place that maps a RodEvent to/from the FIX-JSON
 *                   envelope (header property map + the body as the Text JSON field). toProps takes the BusIdentity
 *                   (bus-id / slot-id / rod-id) and rides the event's own msgType, so the one codec serves every
 *                   x-Rod bus; fromProps reconstructs the event, tolerant of Number-or-String value types.
 * 06/21/2026 mir0n  fromProps hardened on decode: a schema-version evolution gate (a SchemaVersion that is
 *                   present but differs from this codec's is rejected -- logged + IllegalStateException; an
 *                   absent version is tolerated) and intOf / longOf take (map, key) and guard the parse so a
 *                   non-numeric field logs a warn and falls back to 0 instead of throwing NumberFormatException
 *                   (which dropped the whole event). Added a develop-tier logger.
 * 06/22/2026 mir0n  import RodEvent from messaging (was messaging.xrod)
 * 06/23/2026 mir0n  session-message branch in toProps/fromProps (the reduced field set; RequestID omitted on an
 *                   unsolicited HeartBeat); textOf() writes a prepared bodyText to Text (no Map / no Jackson), else the body Map
 * 06/23/2026 mir0n  EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)
 * 06/30/2026 mir0n  the ApplMsgID wire dedup id rides as a header when the event carries one (stamped once on the
 *                   send path, frozen across a resend); fromProps carries it back via RodEvent.withApplMsgId
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes a {@link RodEvent} to a JMS property map (the FIX-JSON envelope) and back. Identity / originator
 * fields ride as header properties; the {@code body} map rides as the {@code Text} JSON field. The stable wire
 * dedup id ({@code ApplMsgID}) rides as a header when the event carries one (stamped once on the send path, frozen
 * across a resend); {@code SendingTime} is the only PER-SEND meta and is still added by the publisher. The codec is
 * a pure, round-trippable mapping.
 */
public final class RodEventCodec {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.messaging.xrod.RodEventCodec");

    private RodEventCodec() {
    }

    /** RodEvent -> the header property map (incl. the body serialized into the Text field). The transport
     *  identity (bus-id / slot-id / rod-id) comes from the {@link BusIdentity}; the msg-type rides from the
     *  event ({@code e.msgType()}). The rod-id is per-message when the event carries one (R&R reply routing: a
     *  responder echoes the requester's rod-id), else the leg's rod-id from the identity; a blank rod-id is
     *  omitted. */
    public static Map<String, Object> toProps(RodEvent e, ObjectMapper om, BusIdentity id) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(BusConstants.FIELD_SCHEMA_VERSION,   BusConstants.SCHEMA_VERSION);
        ret.put(BusConstants.FIELD_BUS_ID,           id.busId());
        ret.put(BusConstants.FIELD_SLOT_ID,       id.slotId());
        String rodId = e.rodId() != null ? e.rodId() : id.rodId();
        if (rodId != null && !rodId.isBlank()) {
            ret.put(BusConstants.FIELD_ROD_ID,       rodId);
        }
        ret.put(BusConstants.FIELD_MSG_TYPE,         e.msgType());
        ret.put(BusConstants.FIELD_MESSAGE_ENCODING, BusConstants.MSG_ENCODING_JSON);
        if (e.applMsgId() != null) {
            // the STABLE wire dedup id (ApplMsgID, FIX 1181), stamped once on the send path -- frozen across a held
            // event's resends so a consumer can dedup. The publisher only adds the per-send SendingTime.
            ret.put(BusConstants.FIELD_APPL_MSG_ID, e.applMsgId());
        }
        if (e.isSession()) {
            // SESSION (alive) message -- the reduced field set: no CRUD fields (EventType / EntityKind / EntityID /
            // SubID / ActionTime / Uid), no header TestReqID (it rides inside Text). RequestID is omitted on an
            // unsolicited HeartBeat (null) and present on a TestRequest / its HeartBeat reply.
            ret.put(BusConstants.FIELD_CORRELATION_ID, e.correlationId());
            if (e.requestId() != null) {
                ret.put(BusConstants.FIELD_REQUEST_ID, e.requestId());
            }
            ret.put(BusConstants.FIELD_TEXT,         textOf(e, om));
        } else {
            ret.put(BusConstants.FIELD_EVENT_TYPE,       e.opCode());
            ret.put(BusConstants.FIELD_ENTITY_KIND,      e.kind());
            ret.put(BusConstants.FIELD_ENTITY_ID,        e.entityId());
            ret.put(BusConstants.FIELD_SUB_ID,           e.subId());
            ret.put(BusConstants.FIELD_ACTION_TIME,      e.actionTime());
            ret.put(BusConstants.FIELD_CORRELATION_ID,   e.correlationId());
            ret.put(BusConstants.FIELD_REQUEST_ID,       e.requestId());
            // keep the R&R wire structure: testReqId rides as the requestId value (the producer guarantees it non-null).
            ret.put(BusConstants.FIELD_TEST_REQ_ID,      e.requestId());
            ret.put(BusConstants.FIELD_UID,              e.uid());
            ret.put(BusConstants.FIELD_TEXT,             textOf(e, om));
        }
        return ret;
    }

    /** The {@code Text} field: a PREPARED {@code bodyText} (a session message rides a ready-made JSON string --
     *  no Map, no serialization) when present, otherwise the {@code body} Map serialized via Jackson. */
    private static String textOf(RodEvent e, ObjectMapper om) {
        return e.bodyText() != null ? e.bodyText() : writeBody(e.body(), om);
    }

    /** Reconstruct a RodEvent from a property map (the Text field carries the body). Two safeguards on decode:
     *  (1) an evolution GATE -- a schema version that is present but differs from this codec's is rejected (see
     *  {@link #requireSchemaVersion}); (2) per-field LENIENCY -- kind / actionTime are tolerant of Number or String
     *  and a single non-numeric value falls back to a default (logged) rather than aborting the whole-event decode. */
    public static RodEvent fromProps(Map<String, Object> p, ObjectMapper om) {
        requireSchemaVersion(p);
        String msgType = str(p.get(BusConstants.FIELD_MSG_TYPE));
        RodEvent ret;
        if (RodEvent.isSession(msgType)) {
            // SESSION (alive) message -- rebuild the bare session envelope (no CRUD fields); TestReqID rides in Text.
            ret = RodEvent.session(msgType,
                    str(p.get(BusConstants.FIELD_CORRELATION_ID)),
                    str(p.get(BusConstants.FIELD_REQUEST_ID)),
                    str(p.get(BusConstants.FIELD_ROD_ID)),
                    readBody(str(p.get(BusConstants.FIELD_TEXT)), om));
        } else {
            ret = new RodEvent(
                    RodEvent.opFromCode(str(p.get(BusConstants.FIELD_EVENT_TYPE))),
                    intOf(p, BusConstants.FIELD_ENTITY_KIND),
                    str(p.get(BusConstants.FIELD_ENTITY_ID)),
                    str(p.get(BusConstants.FIELD_SUB_ID)),
                    longOf(p, BusConstants.FIELD_ACTION_TIME),
                    str(p.get(BusConstants.FIELD_CORRELATION_ID)),
                    str(p.get(BusConstants.FIELD_REQUEST_ID)),
                    str(p.get(BusConstants.FIELD_UID)),
                    str(p.get(BusConstants.FIELD_ROD_ID)),
                    msgType,
                    readBody(str(p.get(BusConstants.FIELD_TEXT)), om));
        }
        // carry the wire dedup id back onto the event (null when absent) so a consumer can read / dedup on it.
        return ret.withApplMsgId(str(p.get(BusConstants.FIELD_APPL_MSG_ID)));
    }

    /** Evolution gate: an incoming message must carry THIS codec's schema version. A version that is PRESENT
     *  but differs is REJECTED (logged + thrown) -- the field mapping of an unknown schema cannot be trusted.
     *  An ABSENT version is tolerated: the sole encoder ({@link #toProps}) always writes the current one, so a
     *  missing value is a legacy/minimal message, not a different schema. */
    private static void requireSchemaVersion(Map<String, Object> p) {
        Object raw = p.get(BusConstants.FIELD_SCHEMA_VERSION);
        if (raw != null) {
            boolean match = raw instanceof Number n
                    ? n.intValue() == BusConstants.SCHEMA_VERSION
                    : String.valueOf(BusConstants.SCHEMA_VERSION).equals(raw.toString().trim());
            if (!match) {
                devLog.error("rod-codec: unsupported schema version '{}' (this codec is {}) -- rejecting the message",
                        raw, BusConstants.SCHEMA_VERSION);
                throw new IllegalStateException("rod-codec: unsupported schema version '" + raw
                        + "' (expected " + BusConstants.SCHEMA_VERSION + ")");
            }
        }
    }

    private static String writeBody(Map<String, Object> body, ObjectMapper om) {
        String ret;
        try {
            ret = om.writeValueAsString(body != null ? body : Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("rod-codec: cannot serialize body", e);
        }
        return ret;
    }

    private static Map<String, Object> readBody(String text, ObjectMapper om) {
        Map<String, Object> ret;
        if (text == null || text.isBlank()) {
            ret = new HashMap<>();
        } else {
            try {
                ret = om.readValue(text, new TypeReference<Map<String, Object>>() { });
            } catch (Exception e) {
                throw new IllegalStateException("rod-codec: cannot deserialize body", e);
            }
        }
        return ret;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    /** A property as an int -- tolerant of Number or String. A non-numeric value does NOT abort the decode
     *  (which would drop the whole event): it is logged and falls back to 0, so the rest of the event survives. */
    private static int intOf(Map<String, Object> p, String key) {
        Object o = p.get(key);
        int ret;
        if (o instanceof Number n) {
            ret = n.intValue();
        } else if (o != null && !o.toString().isBlank()) {
            int v;
            try {
                v = Integer.parseInt(o.toString().trim());
            } catch (NumberFormatException nfe) {
                devLog.warn("rod-codec: field {} has a non-integer value '{}' -- using 0", key, o);
                v = 0;
            }
            ret = v;
        } else {
            ret = 0;
        }
        return ret;
    }

    /** A property as a long -- same leniency as {@link #intOf}: a non-numeric value is logged and falls back to
     *  0 rather than aborting the whole-event decode. */
    private static long longOf(Map<String, Object> p, String key) {
        Object o = p.get(key);
        long ret;
        if (o instanceof Number n) {
            ret = n.longValue();
        } else if (o != null && !o.toString().isBlank()) {
            long v;
            try {
                v = Long.parseLong(o.toString().trim());
            } catch (NumberFormatException nfe) {
                devLog.warn("rod-codec: field {} has a non-long value '{}' -- using 0", key, o);
                v = 0L;
            }
            ret = v;
        } else {
            ret = 0L;
        }
        return ret;
    }
}
