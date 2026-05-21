package pro.mir0n.utils.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BoundedQueueRig} -- the bounded FIFO + worker with a processing gate.
 * Verifies: while processing is OFF the queue is left UNTOUCHED; turning it ON drains in FIFO
 * order; clear() bulk-drops; and a throwing item is routed to the error listener without
 * killing the worker.
 */
class BoundedQueueRigTest {

    private static BoundedQueueRig<String> rig(IQueueRig.IQueueWorker<String> worker) {
        BoundedQueueRig<String> rig = new BoundedQueueRig<>(worker);
        rig.init("test", LoggerFactory.getLogger(BoundedQueueRigTest.class), 16);
        return rig;
    }

    @Test
    @DisplayName("processing OFF leaves items on the queue untouched; ON drains them in FIFO order")
    void processingGate_buffersWhenOff_drainsInOrderWhenOn() throws Exception {
        List<String>   applied = new CopyOnWriteArrayList<>();
        CountDownLatch latch   = new CountDownLatch(3);
        BoundedQueueRig<String> rig = rig(item -> { applied.add(item); latch.countDown(); });

        rig.start();                       // processing starts OFF -> worker parks
        rig.put("a"); rig.put("b"); rig.put("c");

        assertThat(rig.size()).isEqualTo(3);          // queued, untouched
        assertThat(applied).isEmpty();                // worker parked, nothing dequeued

        rig.setProcessing(true);                      // open the gate
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(applied).containsExactly("a", "b", "c");   // FIFO
        assertThat(rig.size()).isZero();
        rig.shutdown();
    }

    @Test
    @DisplayName("clear() bulk-drops queued items -- they are never processed")
    void clear_dropsQueuedItems() throws Exception {
        List<String> applied = new CopyOnWriteArrayList<>();
        BoundedQueueRig<String> rig = rig(applied::add);

        rig.start();                       // processing OFF
        rig.put("a"); rig.put("b"); rig.put("c");
        assertThat(rig.size()).isEqualTo(3);

        rig.clear();
        assertThat(rig.size()).isZero();

        rig.setProcessing(true);
        Thread.sleep(150);                 // give the worker a chance to (wrongly) drain
        assertThat(applied).isEmpty();     // nothing left to process
        rig.shutdown();
    }

    @Test
    @DisplayName("a throwing item is routed to the error listener; the worker keeps running")
    void workerError_routedToListener_workerContinues() throws Exception {
        List<String>   applied = new CopyOnWriteArrayList<>();
        List<String>   errored = new CopyOnWriteArrayList<>();
        CountDownLatch latch   = new CountDownLatch(2);   // ok1, ok2

        BoundedQueueRig<String> rig = rig(item -> {
            if ("boom".equals(item)) throw new RuntimeException("boom");
            applied.add(item);
            latch.countDown();
        });
        rig.setErrorListener((IQueueRig.IErrorListener<String>) (t, item) -> { errored.add(item); return null; });
        rig.setProcessing(true);
        rig.start();

        rig.put("ok1"); rig.put("boom"); rig.put("ok2");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(applied).containsExactly("ok1", "ok2");   // poisoned item didn't stop the worker
        assertThat(errored).containsExactly("boom");
        rig.shutdown();
    }
}
