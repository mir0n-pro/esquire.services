package pro.mir0n.utils.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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

    /* ====================================================================
     * Bulk path -- IQueueListWorker
     * ==================================================================== */

    private static BoundedQueueRig<String> listRig(IQueueRig.IQueueListWorker<String> worker, int cap) {
        BoundedQueueRig<String> rig = new BoundedQueueRig<>(worker);
        rig.init("test-list", LoggerFactory.getLogger(BoundedQueueRigTest.class), cap);
        return rig;
    }

    /** Poll a condition for up to 2s (async worker has no completion latch in some cases). */
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
    @DisplayName("list worker: backlog ABOVE the threshold is handed over as ONE bulk")
    void listWorker_aboveThreshold_handedAsOneBulk() throws Exception {
        List<String>       singles = new CopyOnWriteArrayList<>();
        List<List<String>> bulks   = new CopyOnWriteArrayList<>();
        IQueueRig.IQueueListWorker<String> worker = new IQueueRig.IQueueListWorker<>() {
            @Override public void process(String item) { singles.add(item); }
            @Override public List<String> process(ArrayList<String> items, IQueueRig.ISignaler s) {
                bulks.add(new ArrayList<>(items));
                return null;   // all handled
            }
        };
        BoundedQueueRig<String> rig = listRig(worker, 64);
        rig.setBulkThreshold(3);
        rig.start();                                   // OFF -> items accumulate
        for (String s : List.of("a", "b", "c", "d", "e")) rig.put(s);   // 5 > 3
        rig.setProcessing(true);

        await(() -> bulks.size() == 1);
        assertThat(bulks.get(0)).containsExactly("a", "b", "c", "d", "e");   // whole backlog, in order
        assertThat(singles).isEmpty();
        assertThat(rig.size()).isZero();
        rig.shutdown();
    }

    @Test
    @DisplayName("list worker: backlog AT/BELOW the threshold drains one-by-one (no bulk)")
    void listWorker_atOrBelowThreshold_drainsSingle() throws Exception {
        List<String>       singles = new CopyOnWriteArrayList<>();
        List<List<String>> bulks   = new CopyOnWriteArrayList<>();
        IQueueRig.IQueueListWorker<String> worker = new IQueueRig.IQueueListWorker<>() {
            @Override public void process(String item) { singles.add(item); }
            @Override public List<String> process(ArrayList<String> items, IQueueRig.ISignaler s) {
                bulks.add(new ArrayList<>(items));
                return null;
            }
        };
        BoundedQueueRig<String> rig = listRig(worker, 64);
        rig.setBulkThreshold(5);
        rig.start();                                   // OFF
        for (String s : List.of("a", "b", "c")) rig.put(s);   // 3 <= 5
        rig.setProcessing(true);

        await(() -> singles.size() == 3);
        assertThat(singles).containsExactly("a", "b", "c");   // FIFO, one-by-one
        assertThat(bulks).isEmpty();
        rig.shutdown();
    }

    @Test
    @DisplayName("list worker: returned remainder is re-queued to the front; everything is processed in order")
    void listWorker_remainder_isRequeuedInOrder() throws Exception {
        List<String> processed = new CopyOnWriteArrayList<>();
        IQueueRig.IQueueListWorker<String> worker = new IQueueRig.IQueueListWorker<>() {
            @Override public void process(String item) { processed.add(item); }   // singles after backlog shrinks
            @Override public List<String> process(ArrayList<String> items, IQueueRig.ISignaler s) {
                int take = Math.min(2, items.size());                    // process 2, hand the rest back
                for (int i = 0; i < take; i++) processed.add(items.get(i));
                return new ArrayList<>(items.subList(take, items.size()));
            }
        };
        BoundedQueueRig<String> rig = listRig(worker, 64);
        rig.setBulkThreshold(3);
        rig.start();                                   // OFF
        for (String s : List.of("a", "b", "c", "d", "e", "f", "g")) rig.put(s);   // 7
        rig.setProcessing(true);

        await(() -> processed.size() == 7);
        assertThat(processed).containsExactly("a", "b", "c", "d", "e", "f", "g");   // order preserved across re-queue
        assertThat(rig.size()).isZero();
        rig.shutdown();
    }

    @Test
    @DisplayName("list worker throw: the FULL bulk goes to the list error listener; returning null stops the bulk")
    void listWorker_throw_fullBulkToListListener_nullStops() throws Exception {
        List<List<String>> errBulks = new CopyOnWriteArrayList<>();
        IQueueRig.IQueueListWorker<String> worker = new IQueueRig.IQueueListWorker<>() {
            @Override public void process(String item) { }
            @Override public List<String> process(ArrayList<String> items, IQueueRig.ISignaler s) {
                throw new RuntimeException("bulk boom");
            }
        };
        BoundedQueueRig<String> rig = listRig(worker, 64);
        rig.setBulkThreshold(2);
        rig.setErrorListener(new IQueueRig.IListErrorListener<String>() {
            @Override public String onError(Throwable t, String element) { return null; }
            @Override public List<String> onError(Throwable t, ArrayList<String> items) {
                errBulks.add(new ArrayList<>(items));
                return null;   // stop the bulk
            }
        });
        rig.start();                                   // OFF
        for (String s : List.of("a", "b", "c")) rig.put(s);   // 3 > 2
        rig.setProcessing(true);

        await(() -> errBulks.size() == 1);
        assertThat(errBulks.get(0)).containsExactly("a", "b", "c");   // full bulk as given
        Thread.sleep(120);
        assertThat(errBulks.size()).isEqualTo(1);   // null = stopped; no re-loop
        rig.shutdown();
    }

    @Test
    @DisplayName("list error listener continuation: the worker is re-run on the items it hands back")
    void listWorker_listListener_continuation_isReprocessed() throws Exception {
        List<String>  processed = new CopyOnWriteArrayList<>();
        final boolean[] thrown  = {false};
        IQueueRig.IQueueListWorker<String> worker = new IQueueRig.IQueueListWorker<>() {
            @Override public void process(String item) { }
            @Override public List<String> process(ArrayList<String> items, IQueueRig.ISignaler s) {
                if (!thrown[0]) {                       // fail the first attempt
                    thrown[0] = true;
                    throw new RuntimeException("boom on first bulk");
                }
                processed.addAll(items);                // second attempt: the continuation
                return null;
            }
        };
        BoundedQueueRig<String> rig = listRig(worker, 64);
        rig.setBulkThreshold(2);
        rig.setErrorListener(new IQueueRig.IListErrorListener<String>() {
            @Override public String onError(Throwable t, String element) { return null; }
            @Override public List<String> onError(Throwable t, ArrayList<String> items) {
                return new ArrayList<>(items.subList(1, items.size()));   // drop the head, continue with the rest
            }
        });
        rig.start();                                   // OFF
        for (String s : List.of("a", "b", "c")) rig.put(s);   // 3 > 2
        rig.setProcessing(true);

        await(() -> processed.size() == 2);
        assertThat(processed).containsExactly("b", "c");   // head "a" dropped by the listener, rest re-run
        rig.shutdown();
    }
}
