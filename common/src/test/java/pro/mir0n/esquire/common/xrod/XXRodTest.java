package pro.mir0n.esquire.common.xrod;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class XXRodTest {

    private static RodEvent event(int kind, String id) {
        return new RodEvent(RodEvent.Op.CREATE, kind, id, null, 0L, "c", "r", "u", Map.of());
    }

    @Test
    void appliesEveryEventViaRegisteredRepository() throws Exception {
        int n = 300;
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(n);
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(34, e -> { applied.incrementAndGet(); done.countDown(); });

        XXRod xx = new XXRod(reg, 4, false);
        xx.start("test-apply", null);
        for (int i = 0; i < n; i++) {
            xx.submit(event(34, String.valueOf(i)));
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(n);
        xx.shutdown();
    }

    @Test
    void severalKindsCanShareOneRepository() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(3);
        IRodRepository personLog = e -> { applied.incrementAndGet(); done.countDown(); };
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(992, personLog);
        reg.register(994, personLog);
        reg.register(996, personLog);

        XXRod xx = new XXRod(reg, 2, false);
        xx.start("test-shared", null);
        xx.submit(event(992, "a"));
        xx.submit(event(994, "b"));
        xx.submit(event(996, "c"));

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(3);
        xx.shutdown();
    }

    @Test
    void unregisteredKind_isSkipped_noError() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch sentinel = new CountDownLatch(1);
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(34, e -> { applied.incrementAndGet(); sentinel.countDown(); });

        XXRod xx = new XXRod(reg, 2, false);
        xx.start("test-unreg", null);
        xx.submit(event(99, "x"));   // no repository for kind 99 -> skipped, must not crash a worker
        xx.submit(event(34, "y"));   // this one proves the worker survived

        assertThat(sentinel.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(1);   // only the registered-kind event applied
        xx.shutdown();
    }

    @Test
    void applyThrows_workerSurvives() throws Exception {
        int n = 40;
        CountDownLatch done = new CountDownLatch(n - 1);   // event "13" throws
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(34, e -> {
            if ("13".equals(e.entityId())) {
                throw new RuntimeException("boom");
            }
            done.countDown();
        });

        XXRod xx = new XXRod(reg, 3, false);
        xx.start("test-throw", null);
        for (int i = 0; i < n; i++) {
            xx.submit(event(34, String.valueOf(i)));
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        xx.shutdown();
    }

    @Test
    void workersRunConcurrently() throws Exception {
        int pool = 4;
        CyclicBarrier barrier = new CyclicBarrier(pool);   // trips only if `pool` workers run at once
        AtomicInteger applied = new AtomicInteger();
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(34, e -> {
            try {
                barrier.await(3, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            applied.incrementAndGet();
        });

        XXRod xx = new XXRod(reg, pool, false);
        xx.start("test-conc", null);
        for (int i = 0; i < pool; i++) {
            xx.submit(event(34, String.valueOf(i)));
        }

        long deadline = System.currentTimeMillis() + 5000;
        while (applied.get() < pool && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(applied.get()).isEqualTo(pool);
        xx.shutdown();
    }

    @Test
    void virtualThreads_applyOnVirtualThreads() throws Exception {
        int n = 100;
        CountDownLatch done = new CountDownLatch(n);
        AtomicBoolean allVirtual = new AtomicBoolean(true);
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        reg.register(34, e -> {
            if (!Thread.currentThread().isVirtual()) {
                allVirtual.set(false);
            }
            done.countDown();
        });

        XXRod xx = new XXRod(reg, 4, true);   // virtual-thread pool
        xx.start("test-virt", null);
        for (int i = 0; i < n; i++) {
            xx.submit(event(34, String.valueOf(i)));
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(allVirtual.get()).isTrue();
        xx.shutdown();
    }
}
