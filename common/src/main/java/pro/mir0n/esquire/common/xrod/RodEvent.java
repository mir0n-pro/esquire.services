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
 */
package pro.mir0n.esquire.common.xrod;

import java.util.Map;

/**
 * One relayed entity change. Identity is {@code (entityId, kind, subId)} -- the row the change
 * touched; {@code kind} routes it to a {@link RodRepository} (and thus a {@code *_log} table).
 *
 * <ul>
 *   <li>{@code op} -- CREATE / UPDATE / DELETE (the coalesced, committed op).</li>
 *   <li>{@code kind} -- the (sub)asset kind; the registry key.</li>
 *   <li>{@code entityId} -- the owning entity id (usr_pk / org_pk / acct).</li>
 *   <li>{@code subId} -- discriminator when (entityId, kind) is not unique (ad_pk, par_name); else null.</li>
 *   <li>{@code actionTime} -- epoch-ms stamped at COMMIT; the audit "when".</li>
 *   <li>{@code correlationId} / {@code requestId} / {@code uid} -- the audit triple, snapshotted from
 *       the unified EsqRequestContext at post time so the event is self-contained off the request thread.</li>
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
        Map<String, Object> body
) {
    public enum Op { CREATE, UPDATE, DELETE }
}
