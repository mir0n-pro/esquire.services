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
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes a {@link RodEvent} to a JMS property map (the FIX-JSON envelope) and back. Identity / originator
 * fields ride as header properties; the {@code body} map rides as the {@code Text} JSON field. Envelope
 * meta that is per-send (ApplMsgID, SendingTime) is added by the publisher, not here, so this codec is a
 * pure, round-trippable mapping.
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
        ret.put(EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        ret.put(EsqMsgConstants.FIELD_BUS_ID,           id.busId());
        ret.put(EsqMsgConstants.FIELD_SLOT_ID,       id.slotId());
        String rodId = e.rodId() != null ? e.rodId() : id.rodId();
        if (rodId != null && !rodId.isBlank()) {
            ret.put(EsqMsgConstants.FIELD_ROD_ID,       rodId);
        }
        ret.put(EsqMsgConstants.FIELD_MSG_TYPE,         e.msgType());
        ret.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
        ret.put(EsqMsgConstants.FIELD_EVENT_TYPE,       e.opCode());
        ret.put(EsqMsgConstants.FIELD_ENTITY_KIND,      e.kind());
        ret.put(EsqMsgConstants.FIELD_ENTITY_ID,        e.entityId());
        ret.put(EsqMsgConstants.FIELD_SUB_ID,           e.subId());
        ret.put(EsqMsgConstants.FIELD_ACTION_TIME,      e.actionTime());
        ret.put(EsqMsgConstants.FIELD_CORRELATION_ID,   e.correlationId());
        ret.put(EsqMsgConstants.FIELD_REQUEST_ID,       e.requestId());
        // keep the R&R wire structure: testReqId rides as the requestId value (the producer guarantees it non-null).
        ret.put(EsqMsgConstants.FIELD_TEST_REQ_ID,      e.requestId());
        ret.put(EsqMsgConstants.FIELD_UID,              e.uid());
        ret.put(EsqMsgConstants.FIELD_TEXT,             writeBody(e.body(), om));
        return ret;
    }

    /** Reconstruct a RodEvent from a property map (the Text field carries the body). Two safeguards on decode:
     *  (1) an evolution GATE -- a schema version that is present but differs from this codec's is rejected (see
     *  {@link #requireSchemaVersion}); (2) per-field LENIENCY -- kind / actionTime are tolerant of Number or String
     *  and a single non-numeric value falls back to a default (logged) rather than aborting the whole-event decode. */
    public static RodEvent fromProps(Map<String, Object> p, ObjectMapper om) {
        requireSchemaVersion(p);
        RodEvent ret = new RodEvent(
                RodEvent.opFromCode(str(p.get(EsqMsgConstants.FIELD_EVENT_TYPE))),
                intOf(p, EsqMsgConstants.FIELD_ENTITY_KIND),
                str(p.get(EsqMsgConstants.FIELD_ENTITY_ID)),
                str(p.get(EsqMsgConstants.FIELD_SUB_ID)),
                longOf(p, EsqMsgConstants.FIELD_ACTION_TIME),
                str(p.get(EsqMsgConstants.FIELD_CORRELATION_ID)),
                str(p.get(EsqMsgConstants.FIELD_REQUEST_ID)),
                str(p.get(EsqMsgConstants.FIELD_UID)),
                str(p.get(EsqMsgConstants.FIELD_ROD_ID)),
                str(p.get(EsqMsgConstants.FIELD_MSG_TYPE)),
                readBody(str(p.get(EsqMsgConstants.FIELD_TEXT)), om));
        return ret;
    }

    /** Evolution gate: an incoming message must carry THIS codec's schema version. A version that is PRESENT
     *  but differs is REJECTED (logged + thrown) -- the field mapping of an unknown schema cannot be trusted.
     *  An ABSENT version is tolerated: the sole encoder ({@link #toProps}) always writes the current one, so a
     *  missing value is a legacy/minimal message, not a different schema. */
    private static void requireSchemaVersion(Map<String, Object> p) {
        Object raw = p.get(EsqMsgConstants.FIELD_SCHEMA_VERSION);
        if (raw != null) {
            boolean match = raw instanceof Number n
                    ? n.intValue() == EsqMsgConstants.SCHEMA_VERSION
                    : String.valueOf(EsqMsgConstants.SCHEMA_VERSION).equals(raw.toString().trim());
            if (!match) {
                devLog.error("rod-codec: unsupported schema version '{}' (this codec is {}) -- rejecting the message",
                        raw, EsqMsgConstants.SCHEMA_VERSION);
                throw new IllegalStateException("rod-codec: unsupported schema version '" + raw
                        + "' (expected " + EsqMsgConstants.SCHEMA_VERSION + ")");
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
