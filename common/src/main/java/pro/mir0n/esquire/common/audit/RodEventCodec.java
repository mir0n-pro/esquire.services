/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the x-Rod option (c) wire codec -- the ONE place that maps a RodEvent to/from
 *                   the FIX-JSON envelope (JMS header properties + the body as the Text JSON field). Used by
 *                   the producer bus publisher and the standalone xxRod consumer so both stay in lockstep.
 * 06/08/2026 mir0n  toJson / fromJson added: the same envelope (the toProps map) serialized as a single JSON
 *                   string, for transports whose value is a byte/string payload (Kafka).
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes a {@link RodEvent} to a JMS property map (the FIX-JSON envelope) and back. Identity / audit
 * fields ride as header properties; the {@code body} map rides as the {@code Text} JSON field. Envelope
 * meta that is per-send (ApplMsgID, SendingTime) is added by the publisher, not here, so this codec is a
 * pure, round-trippable mapping.
 */
public final class RodEventCodec {

    private RodEventCodec() {
    }

    /** RodEvent -> the header property map (incl. the body serialized into the Text field). */
    public static Map<String, Object> toProps(RodEvent e, ObjectMapper om) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        ret.put(EsqMsgConstants.FIELD_BUS_ID,           EsqMsgConstants.BUS_ID_ROD);
        ret.put(EsqMsgConstants.FIELD_SERVICE_ID,       EsqMsgConstants.SERVICE_ID_ROD_AUDIT);
        ret.put(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_ROD_AUDIT);
        ret.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
        ret.put(EsqMsgConstants.FIELD_EVENT_TYPE,       opCode(e.op()));
        ret.put(EsqMsgConstants.FIELD_ENTITY_KIND,      e.kind());
        ret.put(EsqMsgConstants.FIELD_ENTITY_ID,        e.entityId());
        ret.put(EsqMsgConstants.FIELD_SUB_ID,           e.subId());
        ret.put(EsqMsgConstants.FIELD_ACTION_TIME,      e.actionTime());
        ret.put(EsqMsgConstants.FIELD_CORRELATION_ID,   e.correlationId());
        ret.put(EsqMsgConstants.FIELD_REQUEST_ID,       e.requestId());
        ret.put(EsqMsgConstants.FIELD_UID,              e.uid());
        ret.put(EsqMsgConstants.FIELD_TEXT,             writeBody(e.body(), om));
        return ret;
    }

    /** Reconstruct a RodEvent from a property map (the Text field carries the body). Tolerant of value
     *  types (kind / actionTime may arrive as a Number or a String, e.g. via JMS string properties). */
    public static RodEvent fromProps(Map<String, Object> p, ObjectMapper om) {
        RodEvent ret = new RodEvent(
                opFrom(str(p.get(EsqMsgConstants.FIELD_EVENT_TYPE))),
                intOf(p.get(EsqMsgConstants.FIELD_ENTITY_KIND)),
                str(p.get(EsqMsgConstants.FIELD_ENTITY_ID)),
                str(p.get(EsqMsgConstants.FIELD_SUB_ID)),
                longOf(p.get(EsqMsgConstants.FIELD_ACTION_TIME)),
                str(p.get(EsqMsgConstants.FIELD_CORRELATION_ID)),
                str(p.get(EsqMsgConstants.FIELD_REQUEST_ID)),
                str(p.get(EsqMsgConstants.FIELD_UID)),
                readBody(str(p.get(EsqMsgConstants.FIELD_TEXT)), om));
        return ret;
    }

    /** RodEvent -> a single JSON string (the property envelope serialized). For transports whose value is a
     *  string / byte payload (e.g. Kafka). */
    public static String toJson(RodEvent e, ObjectMapper om) {
        String ret;
        try {
            ret = om.writeValueAsString(toProps(e, om));
        } catch (Exception ex) {
            throw new IllegalStateException("rod-codec: cannot serialize envelope", ex);
        }
        return ret;
    }

    /** Reconstruct a RodEvent from the JSON envelope produced by {@link #toJson}. */
    public static RodEvent fromJson(String json, ObjectMapper om) {
        try {
            Map<String, Object> p = om.readValue(json, new TypeReference<Map<String, Object>>() { });
            return fromProps(p, om);
        } catch (Exception ex) {
            throw new IllegalStateException("rod-codec: cannot deserialize envelope", ex);
        }
    }

    /** Reconstruct a RodEvent from a received JMS message. */
    public static RodEvent fromMessage(Message m, ObjectMapper om) throws JMSException {
        Map<String, Object> p = new HashMap<>();
        p.put(EsqMsgConstants.FIELD_EVENT_TYPE,     m.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE));
        p.put(EsqMsgConstants.FIELD_ENTITY_KIND,    m.getObjectProperty(EsqMsgConstants.FIELD_ENTITY_KIND));
        p.put(EsqMsgConstants.FIELD_ENTITY_ID,      m.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID));
        p.put(EsqMsgConstants.FIELD_SUB_ID,         m.getStringProperty(EsqMsgConstants.FIELD_SUB_ID));
        p.put(EsqMsgConstants.FIELD_ACTION_TIME,    m.getStringProperty(EsqMsgConstants.FIELD_ACTION_TIME));
        p.put(EsqMsgConstants.FIELD_CORRELATION_ID, m.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID));
        p.put(EsqMsgConstants.FIELD_REQUEST_ID,     m.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID));
        p.put(EsqMsgConstants.FIELD_UID,            m.getStringProperty(EsqMsgConstants.FIELD_UID));
        p.put(EsqMsgConstants.FIELD_TEXT,           m.getStringProperty(EsqMsgConstants.FIELD_TEXT));
        return fromProps(p, om);
    }

    private static String opCode(RodEvent.Op op) {
        String ret;
        switch (op) {
            case CREATE -> ret = EsqMsgConstants.EVENT_CREATE;
            case UPDATE -> ret = EsqMsgConstants.EVENT_UPDATE;
            default     -> ret = EsqMsgConstants.EVENT_DELETE;
        }
        return ret;
    }

    private static RodEvent.Op opFrom(String code) {
        RodEvent.Op ret;
        if (EsqMsgConstants.EVENT_CREATE.equals(code)) {
            ret = RodEvent.Op.CREATE;
        } else if (EsqMsgConstants.EVENT_UPDATE.equals(code)) {
            ret = RodEvent.Op.UPDATE;
        } else {
            ret = RodEvent.Op.DELETE;
        }
        return ret;
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

    private static int intOf(Object o) {
        int ret;
        if (o instanceof Number n) {
            ret = n.intValue();
        } else if (o != null) {
            ret = Integer.parseInt(o.toString());
        } else {
            ret = 0;
        }
        return ret;
    }

    private static long longOf(Object o) {
        long ret;
        if (o instanceof Number n) {
            ret = n.longValue();
        } else if (o != null && !o.toString().isBlank()) {
            ret = Long.parseLong(o.toString());
        } else {
            ret = 0L;
        }
        return ret;
    }
}
