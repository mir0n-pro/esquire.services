package pro.mir0n.utils.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bulk-path tests for {@link AMonadY}: once the backlog passes the rig threshold the monad worker
 * hands a batch of EVENTS to {@link AMonadY#_processItems} (one call instead of N), but FLUSHES the
 * batch before any command so command/event arrival order is preserved.
 */
class AMonadYBulkTest {

    /** A concrete monad that records what the worker did: events seen one-by-one vs in a batch,
     *  the size of each batch, and the overall apply order (events as {@code E:id}, commands {@code C:id}). */
    private static class RecordingMonad extends AMonadY {
        final List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        final List<String>  singles    = new CopyOnWriteArrayList<>();
        final List<String>  order      = new CopyOnWriteArrayList<>();

        RecordingMonad() { super("test", 256); }

        @Override
        protected String _processItem(QueueItem item) {
            if (MonadCmd.CMD == item.eventType()) {
                order.add("C:" + item.entityId());          // command routed through handleCommand
            } else {
                singles.add(item.entityId());
                order.add("E:" + item.entityId());          // single-item event apply
            }
            return null;
        }

        @Override
        protected void _processItems(List<QueueItem> events) {
            batchSizes.add(events.size());                  // one transaction over the batch
            for (QueueItem e : events) {
                order.add("E:" + e.entityId());
            }
        }
    }

    private static QueueItem event(String id) {
        return new QueueItem("U", id, 0, null, "cid", null);
    }

    private static void await(BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within 2s");
    }

    @Test
    @DisplayName("backlog above the threshold is applied as ONE batch (not one-by-one)")
    void aboveThreshold_appliedAsOneBatch() throws Exception {
        RecordingMonad m = new RecordingMonad();
        m.start();                         // processing OFF
        m.setQueueEnabled(true);
        for (int i = 1; i <= 15; i++) {    // 15 > default threshold 10
            m.offer(event("e" + i));
        }
        m.setProcessingEnabled(true);      // open the gate -> worker drains in bulk

        await(() -> m.batchSizes.stream().mapToInt(Integer::intValue).sum() == 15);
        assertThat(m.batchSizes).containsExactly(15);   // a single transaction over the whole backlog
        assertThat(m.singles).isEmpty();                // nothing drained one-by-one
        assertThat(m.queueSize()).isZero();
        m.shutdown();
    }

    @Test
    @DisplayName("a command flushes the event batch before it and order is preserved")
    void commandFlushesBatch_orderPreserved() throws Exception {
        RecordingMonad m = new RecordingMonad();
        m.start();                         // processing OFF
        m.setQueueEnabled(true);

        for (int i = 1; i <= 6; i++) m.offer(event("a" + i));            // 6 events
        m.offer(new QueueItem(MonadCmd.CMD, MonadCmd.CLEAR, 0, null, "cid", null));   // a command
        for (int i = 1; i <= 6; i++) m.offer(event("b" + i));            // 6 events  -> 13 items > 10

        m.setProcessingEnabled(true);

        await(() -> m.order.size() == 13);
        // events before the command commit first, then the command, then the events after it
        assertThat(m.order).containsExactly(
                "E:a1", "E:a2", "E:a3", "E:a4", "E:a5", "E:a6",
                "C:CLEAR",
                "E:b1", "E:b2", "E:b3", "E:b4", "E:b5", "E:b6");
        assertThat(m.batchSizes).containsExactly(6, 6);   // two batches, split by the command
        m.shutdown();
    }
}
