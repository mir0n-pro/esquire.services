/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/30/2026 mir0n  created: the R&R ALIVE-PROTOCOL session -- AliveSession specialised by R&R role. A CLIENT's
 *                   keep-alive is a TestRequest (its rod-id rides so the SERVER's HeartBeat reply routes back); a
 *                   SERVER's is the base unsolicited HeartBeat. On receive a SERVER echoes an arriving TestRequest
 *                   back as a HeartBeat (the URS reply); a CLIENT's session message is liveness only. Everything
 *                   else -- timestamps / health / cadence / send hooks -- is the base AliveSession.
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.function.LongSupplier;

/**
 * The R&R alive session: {@link AliveSession} specialised by R&R {@link Role}. The keep-alive and the arriving
 * session message are role-driven; everything else (the timestamp-age health, the cadence, the send hooks) is the
 * base session.
 * <ul>
 *   <li>{@link #keepAliveEvent} -- a CLIENT probes its SERVER with a TestRequest carrying its own rod-id (so the
 *       SERVER's HeartBeat reply routes back via the RodID selector); a SERVER keeps its response leg alive with
 *       the base unsolicited HeartBeat.</li>
 *   <li>{@link #onReceiveSessn} -- a SERVER answers an arriving TestRequest with a HeartBeat echoing the
 *       requester's correlation + routing (the URS reply path); a CLIENT's received session message is liveness
 *       only (no echo).</li>
 * </ul>
 */
public final class AliveSessionRR extends AliveSession {

    private final Role rrRole;

    public AliveSessionRR(IQueueRig<RodEvent> feed, Role rrRole, long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                          boolean keepAliveEnabled, BusIdentity identity, Logger devLog) {
        this(feed, rrRole, heartbeatIntervalMs, aliveTimeoutMs, failFastOnSendError, keepAliveEnabled, identity, devLog,
                System::currentTimeMillis);
    }

    /** Package-private test seam: an injectable clock makes the cadence deterministic. */
    AliveSessionRR(IQueueRig<RodEvent> feed, Role rrRole, long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                   boolean keepAliveEnabled, BusIdentity identity, Logger devLog,
                   LongSupplier clock) {
        super(feed, heartbeatIntervalMs, aliveTimeoutMs, failFastOnSendError, keepAliveEnabled, identity, devLog,
                clock);
        this.rrRole = rrRole;
    }

    /** R&R keep-alive by role: a CLIENT probes its SERVER with a TestRequest (its own rod-id rides, so the SERVER's
     *  HeartBeat reply routes back via the RodID selector); a SERVER keeps its response leg alive with an
     *  unsolicited HeartBeat (the base keep-alive). */
    @Override
    protected RodEvent keepAliveEvent() {
        RodEvent ret;
        if (rrRole == Role.CLIENT) {
            ret = RodEvent.testRequest(newCorrelationId(), identity.rodId());
        } else {
            ret = super.keepAliveEvent();   // SERVER -> an unsolicited HeartBeat
        }
        return ret;
    }

    /** R&R receive-side: a SERVER answers an arriving TestRequest with a HeartBeat echoing the requester's
     *  correlation + routing (the URS reply path), so the CLIENT observes the round trip. A CLIENT's received
     *  session message is liveness only -- no echo. */
    @Override
    public void onReceiveSessn(RodEvent ev) {
        if (rrRole == Role.SERVER && BusConstants.MSG_TYPE_TEST_REQUEST.equals(ev.msgType())) {
            feed.put(RodEvent.heartbeat(ev.correlationId(), ev.requestId(), ev.rodId()));
        }
    }
}
