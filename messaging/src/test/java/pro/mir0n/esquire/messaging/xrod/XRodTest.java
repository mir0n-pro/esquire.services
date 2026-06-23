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
 * 06/17/2026 mir0n  transmit-leg tests moved to AuditBusBridgeTest (post() lifted out of XRod into the audit
 *                   bridge); this now covers the RECEIVE leg (submit / apply pool) only.
 */
package pro.mir0n.esquire.messaging.xrod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.IRodEventRepo;
import pro.mir0n.esquire.messaging.RodEventRepoRegistry;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class XRodTest {

    /** A receive x-rod: no transport (in-process) + the registry applier as the worker; tests receive() directly. */
    private static XRod receive(RodEventRepoRegistry reg, int poolSize, boolean virtualThreads) {
        XRod rig = new XRod();
        rig.configure(XRodParams.from(Map.of("pool-size", poolSize, "virtual-threads", virtualThreads))
                .withBus("test", "rx", null), Role.CLIENT, null);
        rig.init("test-rx", null);   // CREATE the receive pool (paused) -- before setWorker (the leg must exist)
        rig.setWorker(reg.applier(null));
        rig.start();                 // RUN it (no transport -- receive() is called directly)
        return rig;
    }

    private static RodEvent event(int kind, String id) {
        return new RodEvent(RodEvent.Op.CREATE, kind, id, null, 0L, "c", "r", "u", Map.of());
    }

    // ----------------------------------------------------------------- receive leg (receive / apply pool)

    @Test
    void appliesEveryEventViaRegisteredRepository() throws Exception {
        int n = 300;
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(n);
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> { applied.incrementAndGet(); done.countDown(); });

        XRod rig = receive(reg, 4, false);
        for (int i = 0; i < n; i++) {
            rig.receive(event(34, String.valueOf(i)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(n);
        rig.shutdown();
    }

    @Test
    void shutdownDrainsInFlightBeforeReturning() throws Exception {
        // an apply still running when shutdown() is called must DRAIN before shutdown() returns (so a subclass
        // can then close the transport without cutting off an in-flight send / write).
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> {
            entered.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            applied.incrementAndGet();
        });

        XRod rig = receive(reg, 2, false);
        rig.receive(event(34, "x"));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();   // the apply is in-flight
        rig.shutdown();                                            // must block until it drains
        assertThat(applied.get()).isEqualTo(1);
    }

    @Test
    void receiveAfterShutdownIsDroppedNotThrown() {
        // a late in-flight transport delivery during/after graceful shutdown -- dropped, never thrown.
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        reg.register(34, e -> { });
        XRod rig = receive(reg, 2, false);
        rig.shutdown();
        assertThatCode(() -> rig.receive(event(34, "late"))).doesNotThrowAnyException();
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
        rig.receive(event(992, "a"));
        rig.receive(event(994, "b"));
        rig.receive(event(996, "c"));
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
        rig.receive(event(99, "x"));   // no repository for kind 99 -> skipped, must not crash a worker
        rig.receive(event(34, "y"));   // this one proves the worker survived
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
            rig.receive(event(34, String.valueOf(i)));
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
            rig.receive(event(34, String.valueOf(i)));
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
            rig.receive(event(34, String.valueOf(i)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(allVirtual.get()).isTrue();
        rig.shutdown();
    }
}
