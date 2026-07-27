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
 * 07/09/2026 mir0n  v1.2.11 -- when the registered tracer's aliveTrace() is on: a CLIENT keepAliveEvent() takes its correlation id
 *                   from o11y.IRodTracer.newTraceId() and opens a ROOT producer span (aliveOutbound asRoot=true),
 *                   stamping the traceparent on the TestRequest; a SERVER onReceiveSessn() stamps its HeartBeat
 *                   reply from a nested producer span (asRoot=false)
 * 07/23/2026 mir0n  v1.2.11 -- comment: the SERVER HeartBeat reply uses a BLOCKING put -- a reply can be needed
 *                   while the leg is busy, and dropping it would cause a false SERVER-DOWN at the client
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.o11y.RodObserverHolder;
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
            String traceId = RodObserverHolder.tracer().aliveTrace() ? RodObserverHolder.tracer().newTraceId() : null;
            if (traceId != null) {
                // Traced liveness probe (msg-bus-alive-trace): the correlation id IS the trace id the tracer
                // minted, and a ROOT PRODUCER span is opened for the TestRequest send here (off the cadence, no
                // current span) -- its traceparent rides the wire so the SERVER's HeartBeat reply nests under it:
                // one round-trip trace. The bus never mints a trace id itself; the tracer owns that shape.
                String traceparent = RodObserverHolder.tracer().aliveOutbound(
                        traceId, identity.busId(), "TestRequest", identity.rodId(), true);
                ret = RodEvent.testRequest(traceId, identity.rodId()).withTraceparent(traceparent);
            } else {
                ret = RodEvent.testRequest(newCorrelationId(), identity.rodId());
            }
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
            RodEvent hb = RodEvent.heartbeat(ev.correlationId(), ev.requestId(), ev.rodId());
            if (RodObserverHolder.tracer().aliveTrace() && ev.traceparent() != null) {
                // This runs INSIDE the receive CONSUMER span (AXRod wraps onReceiveSessn), so opening the HeartBeat
                // send as a nested PRODUCER span (asRoot=false) parents it under the receive; its traceparent rides
                // back on the reply so the CLIENT's receive closes the round-trip.
                String traceparent = RodObserverHolder.tracer().aliveOutbound(
                        ev.correlationId(), identity.busId(), "HeartBeat", identity.rodId(), false);
                hb = hb.withTraceparent(traceparent);
            }
            // BLOCKING put, DELIBERATELY. This is the SERVER's HeartBeat REPLY to a client's TestRequest, on the
            // receive path (the RR consumer thread). Unlike the unsolicited tick() heartbeat -- which fires only
            // when the leg is idle, so its feed is empty -- a reply can be needed while this leg is BUSY (feed
            // non-empty). DROPPING it would leave the client without its liveness confirmation, so after
            // alive-timeout the client marks this (healthy, merely busy) SERVER DOWN -- a false failure. So the
            // reply blocks until the feed has room rather than being discarded. Inert today (no leg sets alive).
            feed.put(hb);
        }
    }
}
