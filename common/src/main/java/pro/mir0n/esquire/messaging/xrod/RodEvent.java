/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the x-Rod fan-out event (RoD = Relay of Data). A self-contained snapshot
 *                   of one committed (sub)entity change: header identity (op / kind / entityId / subId),
 *                   the commit-time actionTime, the audit triple (crl / req / uid) snapshotted from
 *                   EsqRequestContext, and the full row body. Carries everything a RodRepository needs
 *                   to apply it to a *_log table on any thread -- and everything to serialize it onto the
 *                   bus for the (c) xx-Rod, with NO request context required downstream.
 * 06/17/2026 mir0n  the 10-arg constructor (rodId, no msgType) removed (unused); javadoc {@link RodRepository}
 *                   -> IRodEventRepo / RodEventRepoRegistry; the msg-type list RDA -> UA
 */
package pro.mir0n.esquire.messaging.xrod;

import pro.mir0n.esquire.common.EsqMsgConstants;

import java.util.Map;

/**
 * One relayed entity change. Identity is {@code (entityId, kind, subId)} -- the row the change
 * touched; {@code kind} routes it to an {@link IRodEventRepo} via {@link RodEventRepoRegistry} (and thus a
 * {@code *_log} table).
 *
 * <ul>
 *   <li>{@code op} -- CREATE / UPDATE / DELETE (the coalesced, committed op).</li>
 *   <li>{@code kind} -- the (sub)asset kind; the registry key.</li>
 *   <li>{@code entityId} -- the owning entity id (usr_pk / org_pk / acct).</li>
 *   <li>{@code subId} -- discriminator when (entityId, kind) is not unique (ad_pk, par_name); else null.</li>
 *   <li>{@code actionTime} -- epoch-ms stamped at COMMIT; the audit "when".</li>
 *   <li>{@code correlationId} / {@code requestId} / {@code uid} -- the audit triple, snapshotted from
 *       the unified EsqRequestContext at post time so the event is self-contained off the request thread.</li>
 *   <li>{@code rodId} -- the ORIGINATING instance id, for R&R reply routing: a responder stamps the
 *       requester's rod-id on the reply so the requester's {@code RodID = '<id>'} selector matches. null on
 *       the one-way buses (audit / broadcast / the request leg) -- the codec then uses the leg's rod-id.</li>
 *   <li>{@code msgType} -- the message type (the {@code EsqMsgConstants.MSG_TYPE_*} value: URQ / URS / URR /
 *       UE / UA). Header info that rides the wire ({@code MsgType}); a responder reads it to tell URS from URR
 *       without inspecting the body. Set by the producer; populated from the wire on receive.</li>
 *   <li>{@code body} -- the full committed row (CREATE/UPDATE); empty on DELETE (id + kind are in the header).</li>
 * </ul>
 */
public record RodEvent(
        Op op,
        int kind,
        String entityId,
        String subId,
        long actionTime,
        String correlationId,
        String requestId,
        String uid,
        String rodId,
        String msgType,
        Map<String, Object> body
) {
    /** Convenience constructor: no per-message rod-id (the codec falls back to the leg's rod-id), no msg-type. */
    public RodEvent(Op op, int kind, String entityId, String subId, long actionTime,
                    String correlationId, String requestId, String uid, Map<String, Object> body) {
        this(op, kind, entityId, subId, actionTime, correlationId, requestId, uid, null, null, body);
    }

    // CREATE / UPDATE / DELETE are the audit ops; UPDATE_PATH ("X", a move / re-path) rides only the
    // entity-broadcast bus -- audit coalesces a move into a plain UPDATE, so it never emits UPDATE_PATH.
    public enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH }

    /** The wire event-type code for this op (the {@code EsqMsgConstants.EVENT_*} value). The canonical
     *  op&lt;-&gt;code mapping lives here on the event so every bus (audit, broadcast) shares it. */
    public String opCode() {
        String ret;
        switch (op) {
            case CREATE      -> ret = EsqMsgConstants.EVENT_CREATE;
            case UPDATE      -> ret = EsqMsgConstants.EVENT_UPDATE;
            case UPDATE_PATH -> ret = EsqMsgConstants.EVENT_UPDATE_PATH;
            default          -> ret = EsqMsgConstants.EVENT_DELETE;
        }
        return ret;
    }

    /** Code -&gt; Op, the inverse of {@link #opCode()}; an unknown code falls back to DELETE. */
    public static Op opFromCode(String code) {
        Op ret;
        if (EsqMsgConstants.EVENT_CREATE.equals(code)) {
            ret = Op.CREATE;
        } else if (EsqMsgConstants.EVENT_UPDATE.equals(code)) {
            ret = Op.UPDATE;
        } else if (EsqMsgConstants.EVENT_UPDATE_PATH.equals(code)) {
            ret = Op.UPDATE_PATH;
        } else {
            ret = Op.DELETE;
        }
        return ret;
    }
}
