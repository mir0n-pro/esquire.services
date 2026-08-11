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
 * 06/22/2026 mir0n  moved to messaging (was messaging.xrod)
 * 06/23/2026 mir0n  session events: bodyText component (a prepared JSON Text string) + an 11-arg delegating ctor;
 *                   isSession + session/heartbeat/testRequest factories (prepared bodyText -- constant + concat); opCode() null-safe
 * 06/23/2026 mir0n  EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)
 * 06/30/2026 mir0n  applMsgId component (the ApplMsgID / FIX 1181 wire dedup id) + a 12-arg canonical ctor; the
 *                   former 11-arg ctor delegates with applMsgId null (stamped once on the send path); withApplMsgId() copy
 * 07/09/2026 mir0n  v1.2.11 -- the record gains a traceparent component (last); the applMsgId-shaped
 *                   constructor leaves it null; withTraceparent(String) copy added, withApplMsgId preserves it
 * 08/11/2026 mir0n  v1.2.12 -- changeNo component added to the identity group and to the producer
 *                   constructor as a normal argument; the record is documented as identity / header /
 *                   payload / engine-stamped tail
 */
package pro.mir0n.esquire.messaging;


import java.util.Map;

/**
 * One relayed change. Identity is {@code (entityId, kind, subId)} PLUS {@code changeNo} -- the row the
 * change touched and WHICH change of that row it is; {@code kind} routes it to an {@link IRodEventRepo} via
 * {@link RodEventRepoRegistry}.
 *
 * <p><b>Component order is a contract, not a habit.</b> Identity first, then the header, then the payload,
 * then a TAIL of fields the ENGINE stamps and a producer never sets ({@code bodyText}, {@code applMsgId},
 * {@code traceparent}). {@code changeNo} sits in the identity group precisely because it is NOT one of those:
 * for an entity event it always exists, supplied by the producer that just raised and wrote it. Keeping it
 * out of the optional tail is what stops "no number" from being an accident -- and what stopped a second
 * trailing-optional constructor from becoming ambiguous with the first.
 *
 * <ul>
 *   <li>{@code op} -- CREATE / UPDATE / DELETE (the coalesced, committed op).</li>
 *   <li>{@code kind} -- the (sub)kind; the registry key.</li>
 *   <li>{@code entityId} -- the owning entity id (usr_pk / org_pk / acct).</li>
 *   <li>{@code subId} -- discriminator when (entityId, kind) is not unique (ad_pk, par_name); else null.</li>
 *   <li>{@code changeNo} -- the (sub)entity CHANGE NUMBER ({@code ChangeNo}, tag 50015); a greater number is
 *       fresher. WHICH counter it is follows the event type: C / U / D carry the ENTITY row's number, X (path)
 *       carries the PATH row's -- see {@code BusConstants.FIELD_CHANGE_NO}, which spells that exception out.
 *       Null only where there is no row behind the message (session events, R&amp;R responses).</li>
 *   <li>{@code actionTime} -- when the change occurred (epoch-ms, stamped at commit).</li>
 *   <li>{@code correlationId} / {@code requestId} / {@code uid} -- the originator ids (correlation /
 *       request / user), snapshotted from the unified EsqRequestContext at post time so the event is
 *       self-contained off the request thread.</li>
 *   <li>{@code rodId} -- the ORIGINATING instance id, for R&R reply routing: a responder stamps the
 *       requester's rod-id on the reply so the requester's {@code RodID = '<id>'} selector matches. null on
 *       the one-way buses (broadcast / the request leg) -- the codec then uses the leg's rod-id.</li>
 *   <li>{@code msgType} -- the message type (the {@code BusConstants.MSG_TYPE_*} value: URQ / URS / URR /
 *       UE / UA). Header info that rides the wire ({@code MsgType}); a responder reads it to tell URS from URR
 *       without inspecting the body. Set by the producer; populated from the wire on receive.</li>
 *   <li>{@code body} -- the full committed row (CREATE/UPDATE); empty on DELETE (id + kind are in the header).</li>
 *   <li>{@code applMsgId} -- the wire dedup id ({@code ApplMsgID}, FIX 1181), STAMPED ONCE on the send path
 *       ({@code AXRod.sendOut}) so a held event's resend reuses the SAME id (a consumer can dedup); null until
 *       stamped, then carried on the wire by the codec (so {@code SendingTime} stays the only per-send meta).</li>
 * </ul>
 */
public record RodEvent(
        // ---- identity: the row this change touched, plus WHICH change of it ----
        Op op,
        int kind,
        String entityId,
        String subId,
        Long changeNo,
        // ---- header ----
        long actionTime,
        String correlationId,
        String requestId,
        String uid,
        String rodId,
        String msgType,
        // ---- payload ----
        Map<String, Object> body,
        // ---- OPTIONAL TAIL: stamped by the ENGINE, never by a producer ----
        String bodyText,
        String applMsgId,
        String traceparent
) {
    /**
     * THE producer constructor -- and, with the canonical one the record generates, the ONLY two ways to build
     * a RodEvent. Deliberately: a family of near-identical overloads is how a positional argument ends up in
     * the wrong slot, and two of them differing only in a trailing type is how a bare {@code null} becomes
     * ambiguous. Anything the producer does not set (the engine's tail) is filled here, once.
     *
     * <p>Everything a producer knows, with the engine's tail left for the engine.
     * {@code changeNo} is a NORMAL argument here, not an optional extra -- for an entity event the number
     * always exists, because the producer has just raised and written it. A message with no row behind it
     * (a session event, an R&amp;R response) passes null.
     */
    public RodEvent(Op op, int kind, String entityId, String subId, Long changeNo, long actionTime,
                    String correlationId, String requestId, String uid, String rodId, String msgType,
                    Map<String, Object> body) {
        this(op, kind, entityId, subId, changeNo, actionTime, correlationId, requestId, uid, rodId, msgType,
                body, null, null, null);
    }

    /** A copy carrying the stable wire dedup id ({@code ApplMsgID}, FIX 1181). Stamped ONCE at the send-chain
     *  entry so every resend of a held event reuses the SAME id (a consumer can dedup); otherwise unchanged. */
    public RodEvent withApplMsgId(String applMsgId) {
        return new RodEvent(op, kind, entityId, subId, changeNo, actionTime, correlationId, requestId, uid,
                rodId, msgType, body, bodyText, applMsgId, traceparent);
    }

    /** A copy carrying the W3C {@code traceparent} for the bus hop (v1.2.11 O2/T3). Stamped ONCE on the send
     *  path (non-session events); the trace id half is the correlationId, the span id half the producer's
     *  parent span. Otherwise unchanged (applMsgId preserved). */
    public RodEvent withTraceparent(String traceparent) {
        return new RodEvent(op, kind, entityId, subId, changeNo, actionTime, correlationId, requestId, uid,
                rodId, msgType, body, bodyText, applMsgId, traceparent);
    }

    // CREATE / UPDATE / DELETE are the change operations; UPDATE_PATH ("X", a move / re-path) rides only the
    // entity-broadcast bus -- a one-way sink may coalesce a move into a plain UPDATE and never emit UPDATE_PATH.
    public enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH }

    /** The wire event-type code for this op (the {@code BusConstants.EVENT_*} value). The canonical
     *  op&lt;-&gt;code mapping lives here on the event so every bus shares it. A session (admin) event carries no
     *  CRUD op -- {@code op} is null and this returns null (the codec omits {@code EventType} for it). */
    public String opCode() {
        String ret;
        if (op == null) {
            ret = null;
        } else {
            switch (op) {
                case CREATE      -> ret = BusConstants.EVENT_CREATE;
                case UPDATE      -> ret = BusConstants.EVENT_UPDATE;
                case UPDATE_PATH -> ret = BusConstants.EVENT_UPDATE_PATH;
                default          -> ret = BusConstants.EVENT_DELETE;
            }
        }
        return ret;
    }

    /** Code -&gt; Op, the inverse of {@link #opCode()}; an unknown code falls back to DELETE. */
    public static Op opFromCode(String code) {
        Op ret;
        if (BusConstants.EVENT_CREATE.equals(code)) {
            ret = Op.CREATE;
        } else if (BusConstants.EVENT_UPDATE.equals(code)) {
            ret = Op.UPDATE;
        } else if (BusConstants.EVENT_UPDATE_PATH.equals(code)) {
            ret = Op.UPDATE_PATH;
        } else {
            ret = Op.DELETE;
        }
        return ret;
    }

    // ------------------------------------------------------------------ session (alive protocol) events

    /** Whether {@code msgType} is an x-rod SESSION (alive-protocol) type -- a HeartBeat or a TestRequest. The
     *  x-rod handles these internally (never forwards them to the application worker). */
    public static boolean isSession(String msgType) {
        return BusConstants.MSG_TYPE_HEARTBEAT.equals(msgType)
                || BusConstants.MSG_TYPE_TEST_REQUEST.equals(msgType);
    }

    /** Whether THIS event is a session (alive-protocol) event. */
    public boolean isSession() {
        return isSession(msgType);
    }

    // Prepared session Text bodies -- built ONCE at class load (the unsolicited HeartBeat) or filled by a single
    // concat (the TestReqID variants). The session body is fixed-shape, so a heartbeat costs NO Map allocation and
    // NO Jackson serialization: the ready string rides as the event's bodyText, written straight to the wire.
    private static final String HEARTBEAT_BODY =
            "{\"" + BusConstants.FIELD_MSG_TYPE + "\":\"" + BusConstants.MSG_TYPE_HEARTBEAT + "\"}";
    private static final String HEARTBEAT_TR_OPEN =
            "{\"" + BusConstants.FIELD_MSG_TYPE + "\":\"" + BusConstants.MSG_TYPE_HEARTBEAT
            + "\",\"" + BusConstants.FIELD_TEST_REQ_ID + "\":\"";
    private static final String TESTREQUEST_OPEN =
            "{\"" + BusConstants.FIELD_MSG_TYPE + "\":\"" + BusConstants.MSG_TYPE_TEST_REQUEST
            + "\",\"" + BusConstants.FIELD_TEST_REQ_ID + "\":\"";
    private static final String BODY_CLOSE = "\"}";

    /** A bare session-event envelope (DECODE side): no CRUD op / kind / entity -- only the routing identity, the
     *  correlation, the msg-type, and the parsed body Map. Used by the codec when reading a session message off
     *  the wire (the produce side uses the prepared-{@code bodyText} factories below). */
    public static RodEvent session(String msgType, String correlationId, String requestId, String rodId,
                                   Map<String, Object> body) {
        return new RodEvent(null, 0, null, null, null, 0L, correlationId, requestId, null, rodId, msgType, body);
    }

    /** A HeartBeat (MsgType "0"): unsolicited (a broadcast / R&R SERVER keepalive -- {@code requestId}/{@code rodId}
     *  null, a fresh {@code correlationId}) or a response to a TestRequest (the requester's {@code rodId} +
     *  echoed {@code correlationId}/{@code requestId}). The {@code Text} body is the prepared string -- the
     *  constant {@link #HEARTBEAT_BODY} when unsolicited, else the template filled with the echoed TestReqID. */
    public static RodEvent heartbeat(String correlationId, String requestId, String rodId) {
        String text = requestId != null ? HEARTBEAT_TR_OPEN + requestId + BODY_CLOSE : HEARTBEAT_BODY;
        return new RodEvent(null, 0, null, null, null, 0L, correlationId, requestId, null, rodId,
                BusConstants.MSG_TYPE_HEARTBEAT, Map.of(), text, null, null);
    }

    /** A TestRequest (MsgType "1"): an R&R CLIENT probe on inactivity. {@code requestId} = {@code correlationId};
     *  {@code rodId} null (the leg's own rod-id rides, so the SERVER's HeartBeat reply routes back). The
     *  {@code Text} body is the prepared template filled with the TestReqID (= {@code correlationId}). */
    public static RodEvent testRequest(String correlationId, String rodId) {
        String text = TESTREQUEST_OPEN + correlationId + BODY_CLOSE;
        return new RodEvent(null, 0, null, null, null, 0L, correlationId, correlationId, null, rodId,
                BusConstants.MSG_TYPE_TEST_REQUEST, Map.of(), text, null, null);
    }
}
