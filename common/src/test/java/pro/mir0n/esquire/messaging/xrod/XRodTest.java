/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/13/2026 mir0n  created: the XRod transceiver tests -- the TRANSMIT leg (post/buffer/after-commit, was
 *                   XYRodTest) wired with outbound = a capture sink, and the RECEIVE leg (submit/apply pool,
 *                   was XXRodTest) wired with receiveWorker = a registry applier. Replaces XYRodTest + XXRodTest.
 */
package pro.mir0n.esquire.messaging.xrod;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class XRodTest {

    private final List<RodEvent> received = new CopyOnWriteArrayList<>();

    /** A transmit pod that captures: no transport (so in-process) + a capture worker -- post -> feed -> own pool
     *  -> worker exercises the whole transmit path (buffer / after-commit / audit-triple). {@code enabled=false}
     *  = no worker + no transport -> no transmit leg (a no-op pod), matching the audit-off case. */
    private XRod transmit(boolean enabled) {
        XRod rig = new XRod();
        rig.configure(XRodParams.from(Map.of()).withBus("test", "tx", null), Role.BROADCAST, null);
        rig.start("test-tx", null, enabled ? received::add : null);
        return rig;
    }

    /** A receive pod: no transport (in-process) + the registry applier as the worker; tests submit() directly. */
    private static XRod receive(RodEventRepoRegistry reg, int poolSize, boolean virtualThreads) {
        XRod rig = new XRod();
        rig.configure(XRodParams.from(Map.of("pool-size", poolSize, "virtual-threads", virtualThreads))
                .withBus("test", "rx", null), Role.BROADCAST, null);
        rig.start("test-rx", null, reg.applier(null));
        return rig;
    }

    private static RodEvent event(int kind, String id) {
        return new RodEvent(RodEvent.Op.CREATE, kind, id, null, 0L, "c", "r", "u", Map.of());
    }

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    private void fireCompletion(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(status);
        }
    }

    private void inCommittedTx(Runnable body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
            fireAfterCommit();
            fireCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void awaitSize(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    // ----------------------------------------------------------------- transmit leg (post / feed)

    @Test
    void onePerEntity_inOneTx_allEmittedAfterCommit() throws Exception {
        XRod rig = transmit(true);
        inCommittedTx(() -> {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"), "U");
            rig.post(RodEvent.Op.UPDATE, 992, "100", null, Map.of("first", "Ann"), "U");
            rig.post(RodEvent.Op.UPDATE, 988, "100", "55", Map.of("city", "NYC"), "U");
        });
        awaitSize(3, 3000);
        assertThat(received).hasSize(3);
        assertThat(received).allMatch(e -> e.actionTime() > 0L);
        rig.shutdown();
    }

    @Test
    void delete_hasEmptyBody() throws Exception {
        XRod rig = transmit(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 34, "100", null, Map.of("ignored", "x"), "U"));
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    @Test
    void buffered_nothingEmittedBeforeCommit() throws Exception {
        XRod rig = transmit(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"), "U");
            Thread.sleep(120);
            assertThat(received).isEmpty();
            fireAfterCommit();
            fireCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        rig.shutdown();
    }

    @Test
    void rolledBack_emitsNothing() throws Exception {
        XRod rig = transmit(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"), "U");
            fireCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        Thread.sleep(150);
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void disabled_post_isNoOp() throws Exception {
        XRod rig = transmit(false);
        assertThat(rig.isEnabled()).isFalse();
        inCommittedTx(() -> rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"), "U"));
        Thread.sleep(150);
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void event_carriesAuditTripleFromContext() throws Exception {
        EsqContextHolder.set(new EsqRequestContext("corr-1", "req-1", "uid-9", "1.2."));
        XRod rig = transmit(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"), "U"));
        awaitSize(1, 3000);
        RodEvent e = received.get(0);
        assertThat(e.correlationId()).isEqualTo("corr-1");
        assertThat(e.requestId()).isEqualTo("req-1");
        assertThat(e.uid()).isEqualTo("uid-9");
        rig.shutdown();
    }

    @Test
    void noActiveTransaction_feedsImmediately() throws Exception {
        XRod rig = transmit(true);
        rig.post(RodEvent.Op.UPDATE, 34, "100", null, Map.of("name", "ACME"), "U");
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.UPDATE);
        rig.shutdown();
    }

    @Test
    void post_withMappable_buildsBodyViaFillMap() throws Exception {
        XRod rig = transmit(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME", "ccy", "USD"));
        inCommittedTx(() -> rig.post(RodEvent.Op.UPDATE, 50, "100", null, src, "U"));
        awaitSize(1, 3000);
        assertThat(src.filled).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).body()).containsEntry("name", "ACME").containsEntry("ccy", "USD");
        rig.shutdown();
    }

    @Test
    void post_withMappable_onDelete_skipsFillMap_emptyBody() throws Exception {
        XRod rig = transmit(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 50, "100", null, src, "U"));
        awaitSize(1, 3000);
        assertThat(src.filled).isFalse();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    @Test
    void post_withMappable_whenDisabled_skipsFillMapAndEmitsNothing() throws Exception {
        XRod rig = transmit(false);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> rig.post(RodEvent.Op.UPDATE, 50, "100", null, src, "U"));
        Thread.sleep(150);
        assertThat(src.filled).isFalse();
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void post_noBodyOverload_delete_emptyBody() throws Exception {
        XRod rig = transmit(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 50, "100", null, "U"));
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    // ----------------------------------------------------------------- receive leg (submit / apply pool)

    @Test
    void appliesEveryEventViaRegisteredRepository() throws Exception {
        int n = 300;
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(n);
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> { applied.incrementAndGet(); done.countDown(); });

        XRod rig = receive(reg, 4, false);
        for (int i = 0; i < n; i++) {
            rig.submit(event(34, String.valueOf(i)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(n);
        rig.shutdown();
    }

    @Test
    void severalKindsCanShareOneRepository() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(3);
        IRodEventRepo personLog = e -> { applied.incrementAndGet(); done.countDown(); };
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(992, personLog);
        reg.register(994, personLog);
        reg.register(996, personLog);

        XRod rig = receive(reg, 2, false);
        rig.submit(event(992, "a"));
        rig.submit(event(994, "b"));
        rig.submit(event(996, "c"));
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(3);
        rig.shutdown();
    }

    @Test
    void unregisteredKind_isSkipped_noError() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch sentinel = new CountDownLatch(1);
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> { applied.incrementAndGet(); sentinel.countDown(); });

        XRod rig = receive(reg, 2, false);
        rig.submit(event(99, "x"));   // no repository for kind 99 -> skipped, must not crash a worker
        rig.submit(event(34, "y"));   // this one proves the worker survived
        assertThat(sentinel.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(1);
        rig.shutdown();
    }

    @Test
    void applyThrows_workerSurvives() throws Exception {
        int n = 40;
        CountDownLatch done = new CountDownLatch(n - 1);   // event "13" throws
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> {
            if ("13".equals(e.entityId())) {
                throw new RuntimeException("boom");
            }
            done.countDown();
        });

        XRod rig = receive(reg, 3, false);
        for (int i = 0; i < n; i++) {
            rig.submit(event(34, String.valueOf(i)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        rig.shutdown();
    }

    @Test
    void workersRunConcurrently() throws Exception {
        int pool = 4;
        CyclicBarrier barrier = new CyclicBarrier(pool);
        AtomicInteger applied = new AtomicInteger();
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> {
            try {
                barrier.await(3, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            applied.incrementAndGet();
        });

        XRod rig = receive(reg, pool, false);
        for (int i = 0; i < pool; i++) {
            rig.submit(event(34, String.valueOf(i)));
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (applied.get() < pool && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(applied.get()).isEqualTo(pool);
        rig.shutdown();
    }

    @Test
    void virtualThreads_applyOnVirtualThreads() throws Exception {
        int n = 100;
        CountDownLatch done = new CountDownLatch(n);
        AtomicBoolean allVirtual = new AtomicBoolean(true);
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> {
            if (!Thread.currentThread().isVirtual()) {
                allVirtual.set(false);
            }
            done.countDown();
        });

        XRod rig = receive(reg, 4, true);   // virtual-thread pool
        for (int i = 0; i < n; i++) {
            rig.submit(event(34, String.valueOf(i)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(allVirtual.get()).isTrue();
        rig.shutdown();
    }

    /** An IMappable that records whether fillMap was invoked, so the overload's guards can be asserted. */
    private static final class CountingMappable implements pro.mir0n.esquire.backend.jpa.IMappable {
        private final Map<String, Object> data;
        private boolean filled = false;

        CountingMappable(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public void fillMap(Map<String, Object> body) {
            filled = true;
            body.putAll(data);
        }
    }
}
