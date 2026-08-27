/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/11/2026 mir0n  created: the ONE bus-hop observer umbrella (v1.2.11 O1/T5). Trace and metrics live under one
 *                   umbrella at the bus hop: this type joins IRodTracer + IRodMeters so a single host object
 *                   (EsqRodObserver, carrying both the OTel Tracer and the Micrometer registry) covers both, is
 *                   held once in RodObserverHolder, and is registered by one bean. The interfaces stay separate
 *                   (concern separation); only the object / holder / registrar / umbrella switch are unified.
 * 08/26/2026 mir0n  registerTransportUp forwarded to the meters, and to IRodMeters.NOOP when unobserved
 */
package pro.mir0n.esquire.messaging.o11y;

/**
 * The single bus-hop observer -- both {@link IRodTracer} and {@link IRodMeters} in one type. The x-rod engine reads
 * ONE object from {@link RodObserverHolder} and uses it as a tracer at the trace seams and as meters at the metric
 * seams. A concrete implementation is the host application's observability layer (holding both the OTel Tracer and
 * the Micrometer registry); {@link #NOOP} combines the two NOOPs so the engine pays nothing when observability is off.
 */
public interface IRodObserver extends IRodTracer, IRodMeters {

    /** Compose a bus observer from a separate tracer and meters -- the trace seams delegate to {@code tracer},
     *  the metric seams to {@code meters}. A NOOP on either side runs that side free. (The host's real observer,
     *  EsqRodObserver, implements both directly; this is for composition / tests.) */
    static IRodObserver of(IRodTracer tracer, IRodMeters meters) {
        return new IRodObserver() {
            @Override public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
                return tracer.outbound(correlationId, busId, slotId, ownRodId);
            }
            @Override public void inbound(String traceparent, String correlationId, String busId, String slotId, String fromRodId, String ownRodId, Runnable worker) {
                tracer.inbound(traceparent, correlationId, busId, slotId, fromRodId, ownRodId, worker);
            }
            @Override public boolean aliveTrace() {
                return tracer.aliveTrace();
            }
            @Override public String newTraceId() {
                return tracer.newTraceId();
            }
            @Override public String aliveOutbound(String correlationId, String busId, String label, String ownRodId, boolean asRoot) {
                return tracer.aliveOutbound(correlationId, busId, label, ownRodId, asRoot);
            }
            @Override public void aliveInbound(String traceparent, String correlationId, String busId, String label, String fromRodId, String ownRodId, Runnable worker) {
                tracer.aliveInbound(traceparent, correlationId, busId, label, fromRodId, ownRodId, worker);
            }
            @Override public void sent(String busId, String slotId, String msgType) {
                meters.sent(busId, slotId, msgType);
            }
            @Override public void sendDuration(String busId, String slotId, String msgType, long nanos) {
                meters.sendDuration(busId, slotId, msgType, nanos);
            }
            @Override public void received(String busId, String slotId, String msgType) {
                meters.received(busId, slotId, msgType);
            }
            @Override public void error(String busId, String slotId, String msgType, String leg) {
                meters.error(busId, slotId, msgType, leg);
            }
            @Override public void retryBackoff(String busId, long backoffMs) {
                meters.retryBackoff(busId, backoffMs);
            }
            @Override public void retryDropped(String busId, String msgType) {
                meters.retryDropped(busId, msgType);
            }
            @Override public void registerFeedDepth(String busId, String slotId, java.util.function.IntSupplier depth) {
                meters.registerFeedDepth(busId, slotId, depth);
            }
            @Override public void registerRetryHeld(String busId, String slotId, java.util.function.IntSupplier held) {
                meters.registerRetryHeld(busId, slotId, held);
            }
            @Override public void registerTransportUp(String busId, java.util.function.IntSupplier up) {
                meters.registerTransportUp(busId, up);
            }
        };
    }

    /** No-op observer -- the engine's default until the host registers one. Delegates each side to its own NOOP. */
    IRodObserver NOOP = new IRodObserver() {
        // --- tracer side (delegate to IRodTracer.NOOP) ---
        @Override public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
            return IRodTracer.NOOP.outbound(correlationId, busId, slotId, ownRodId);
        }
        @Override public void inbound(String traceparent, String correlationId, String busId, String slotId, String fromRodId, String ownRodId, Runnable worker) {
            IRodTracer.NOOP.inbound(traceparent, correlationId, busId, slotId, fromRodId, ownRodId, worker);
        }
        @Override public boolean aliveTrace() {
            return IRodTracer.NOOP.aliveTrace();
        }
        @Override public String newTraceId() {
            return IRodTracer.NOOP.newTraceId();
        }
        @Override public String aliveOutbound(String correlationId, String busId, String label, String ownRodId, boolean asRoot) {
            return IRodTracer.NOOP.aliveOutbound(correlationId, busId, label, ownRodId, asRoot);
        }
        @Override public void aliveInbound(String traceparent, String correlationId, String busId, String label, String fromRodId, String ownRodId, Runnable worker) {
            IRodTracer.NOOP.aliveInbound(traceparent, correlationId, busId, label, fromRodId, ownRodId, worker);
        }
        // --- meters side (delegate to IRodMeters.NOOP) ---
        @Override public void sent(String busId, String slotId, String msgType) {
            IRodMeters.NOOP.sent(busId, slotId, msgType);
        }
        @Override public void sendDuration(String busId, String slotId, String msgType, long nanos) {
            IRodMeters.NOOP.sendDuration(busId, slotId, msgType, nanos);
        }
        @Override public void received(String busId, String slotId, String msgType) {
            IRodMeters.NOOP.received(busId, slotId, msgType);
        }
        @Override public void error(String busId, String slotId, String msgType, String leg) {
            IRodMeters.NOOP.error(busId, slotId, msgType, leg);
        }
        @Override public void retryBackoff(String busId, long backoffMs) {
            IRodMeters.NOOP.retryBackoff(busId, backoffMs);
        }
        @Override public void retryDropped(String busId, String msgType) {
            IRodMeters.NOOP.retryDropped(busId, msgType);
        }
        @Override public void registerFeedDepth(String busId, String slotId, java.util.function.IntSupplier depth) {
            IRodMeters.NOOP.registerFeedDepth(busId, slotId, depth);
        }
        @Override public void registerRetryHeld(String busId, String slotId, java.util.function.IntSupplier held) {
            IRodMeters.NOOP.registerRetryHeld(busId, slotId, held);
        }
        @Override public void registerTransportUp(String busId, java.util.function.IntSupplier up) {
            IRodMeters.NOOP.registerTransportUp(busId, up);
        }
    };
}
