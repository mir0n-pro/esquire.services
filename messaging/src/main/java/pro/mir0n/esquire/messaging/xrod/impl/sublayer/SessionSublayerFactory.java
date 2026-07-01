/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: builds the x-rod producer session-sublayer LIST, so the engine (AXRod) never names a
 *                   concrete sublayer. From the leg config it builds the alive keepalive (opt-in 'alive'; an
 *                   AliveSessionRR when an R&R role is given, else the base AliveSession) and the send-retry policy
 *                   (opt-in 'send-retry', only with a transport publisher), ordered alive FIRST (it observes every
 *                   send outcome before send-retry's hold) then send-retry; either may be absent. The feed (tx)
 *                   worker drives the returned hooks and ticks them on idle(). Future producer R patterns are added
 *                   HERE, not in the engine.
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.xrod.RodPublisher;
import pro.mir0n.esquire.messaging.xrod.impl.ISessionSublayer;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.ArrayList;
import java.util.List;

/** Factory for the producer session-sublayer stack: the seam that keeps the x-rod engine free of concrete sublayer
 *  types. It BUILDS the stack per leg config -- the alive keepalive (per role) and the producer send-retry policy --
 *  and returns the ordered list the engine's feed (tx) worker calls and ticks on idle(). Future producer patterns
 *  are added here, not in the engine. */
public final class SessionSublayerFactory {

    private static final int     DEFAULT_HEARTBEAT_INTERVAL_SEC = 10;
    private static final int     ALIVE_TIMEOUT_FACTOR           = 3;     // alive-timeout default = factor x heartbeat-interval
    private static final boolean DEFAULT_ALIVE_FAIL_FAST        = true;  // provisional: a send error flips DOWN at once (default to LEARN)

    private SessionSublayerFactory() {
    }

    /** Build the producer session stack. {@code publisher} (null for a non-transport / in-process outbound) is the
     *  transport leg; {@code feed} is the transmit queue a heartbeat is PUT on. The alive session is built when the
     *  leg's {@code alive} param is on -- an {@link AliveSessionRR} when an R&R {@code rrRole} is given (CLIENT /
     *  SERVER drive the keep-alive + the session-message echo), else the base {@link AliveSession}; {@code
     *  keepAliveEnabled} marks a producing leg. Send-retry is built when {@code send-retry} is on AND there is a
     *  transport publisher (encode-once + throwing-dispatch). The list is ordered alive FIRST -- so it observes
     *  every send outcome (its marks + fail-fast) BEFORE send-retry's blocking hold -- then send-retry; either may
     *  be absent. */
    public static List<ISessionSublayer> build(XRodParams params,
                                     RodPublisher publisher,
                                     IQueueRig<RodEvent> feed,
                                     BusIdentity identity,
                                     Logger devLog,
                                     boolean keepAliveEnabled,
                                     Role rrRole) {
        AliveSession alive = null;
        SendRetrySublayer retry = null;
        if (params != null && params.aliveOr(false)) {
            int heartbeatSec = params.heartbeatIntervalSecOr(DEFAULT_HEARTBEAT_INTERVAL_SEC);
            int timeoutSec   = params.aliveTimeoutSecOr(heartbeatSec * ALIVE_TIMEOUT_FACTOR);
            boolean failFast = params.aliveFailFastOr(DEFAULT_ALIVE_FAIL_FAST);
            if (rrRole != null) {
                alive = new AliveSessionRR(feed, rrRole, heartbeatSec * 1000L, timeoutSec * 1000L, failFast,
                        keepAliveEnabled, identity, devLog);
            } else {
                alive = new AliveSession(feed, heartbeatSec * 1000L, timeoutSec * 1000L, failFast,
                        keepAliveEnabled, identity, devLog);
            }
        }
        if (params != null && params.sendRetryOr(false) && publisher != null) {
            retry = new SendRetrySublayer(params.sendRetryBackoff(), params.sendRetryMaxAttemptsOr(0), identity);
        }
        List<ISessionSublayer> ret = new ArrayList<>();
        if (alive != null) {
            ret.add(alive);   // alive FIRST (the base layer): observes every send outcome + drives the heartbeat
        }
        if (retry != null) {
            ret.add(retry);   // send-retry after: the hold + re-send
        }
        return ret;
    }
}
