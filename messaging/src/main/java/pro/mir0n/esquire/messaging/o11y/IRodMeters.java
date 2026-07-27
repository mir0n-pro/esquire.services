/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/11/2026 mir0n  created: the bus-hop METER hook, DECLARED by the messaging bus (v1.2.11 O1/T5). The mirror of
 *                   IRodTracer for metrics: the bus deals only in String / primitives / IntSupplier through it and
 *                   imports no Micrometer (or anything above itself); the Micrometer-backed implementation is the
 *                   host application's, handed in via RodObserverHolder as part of the ONE bus observer. Counters
 *                   ride the observability umbrella; the host decides what is a costly histogram. NOOP = zero cost.
 */
package pro.mir0n.esquire.messaging.o11y;

import java.util.function.IntSupplier;

/**
 * The bus-hop meter hook -- the generic seam the x-rod engine calls so the bus emits its own metrics without the
 * {@code messaging} module depending on Micrometer or on anything above it. Declared beside its only caller (the
 * x-rod engine); the concrete implementation is supplied by the host application's observability layer as part of
 * the single bus {@link IRodObserver} and registered through {@link RodObserverHolder}. Off by default
 * ({@link #NOOP}) -- the engine pays nothing when observability is disabled.
 */
public interface IRodMeters {

    /** A message landed on the producing leg: bump {@code messaging.send.total}. Tags: bus-id / slot / msgType. */
    void sent(String busId, String slotId, String msgType);

    /** Record the owned-send wall time {@code messaging.send.duration} ({@code nanos}), timed around the tx
     *  worker's send. Tags: bus-id / slot / msgType. */
    void sendDuration(String busId, String slotId, String msgType, long nanos);

    /** A message was dispatched to the app worker on the consuming leg: bump {@code messaging.receive.total}.
     *  Tags: bus-id / slot / msgType. */
    void received(String busId, String slotId, String msgType);

    /** A send or receive failed: bump {@code messaging.error.total}. {@code leg} = "send" | "receive".
     *  Tags: bus-id / slot / msgType / leg. */
    void error(String busId, String slotId, String msgType, String leg);

    /** The send-retry sublayer held a failed send for a backoff: record {@code messaging.retry.backoff}
     *  ({@code backoffMs}). Tag: bus-id. */
    void retryBackoff(String busId, long backoffMs);

    /** The send-retry sublayer gave up on a held send (max attempts): bump {@code messaging.retry.dropped}.
     *  Tags: bus-id / msgType. */
    void retryDropped(String busId, String msgType);

    /** Register {@code messaging.feed.depth} as a gauge over {@code depth} (the transmit feed's live size),
     *  called ONCE when the engine builds its feed. Tags: bus-id / slot. */
    void registerFeedDepth(String busId, String slotId, IntSupplier depth);

    /** Register {@code messaging.retry.held} as a gauge over {@code held} (the send-retry sublayer's current
     *  hold depth), called ONCE when the sublayer is built. Tags: bus-id / slot. */
    void registerRetryHeld(String busId, String slotId, IntSupplier held);

    /** No-op meters -- the engine's default when the host has registered no observer (observability off). */
    IRodMeters NOOP = new IRodMeters() {
        @Override public void sent(String busId, String slotId, String msgType) {
        }
        @Override public void sendDuration(String busId, String slotId, String msgType, long nanos) {
        }
        @Override public void received(String busId, String slotId, String msgType) {
        }
        @Override public void error(String busId, String slotId, String msgType, String leg) {
        }
        @Override public void retryBackoff(String busId, long backoffMs) {
        }
        @Override public void retryDropped(String busId, String msgType) {
        }
        @Override public void registerFeedDepth(String busId, String slotId, IntSupplier depth) {
        }
        @Override public void registerRetryHeld(String busId, String slotId, IntSupplier held) {
        }
    };
}
