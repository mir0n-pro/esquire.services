/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.utils.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerPoolTest {

    @Test
    void mode_of_parsesTokens_defaultsPlatform() {
        assertThat(WorkerPool.Mode.of("platform")).isEqualTo(WorkerPool.Mode.PLATFORM);
        assertThat(WorkerPool.Mode.of("virtual")).isEqualTo(WorkerPool.Mode.VIRTUAL);
        assertThat(WorkerPool.Mode.of("virtual-per-task")).isEqualTo(WorkerPool.Mode.VIRTUAL_PER_TASK);
        assertThat(WorkerPool.Mode.of(" VIRTUAL ")).isEqualTo(WorkerPool.Mode.VIRTUAL);   // trim + case-insensitive
        assertThat(WorkerPool.Mode.of(null)).isEqualTo(WorkerPool.Mode.PLATFORM);         // blank -> safe default
        assertThat(WorkerPool.Mode.of("nonsense")).isEqualTo(WorkerPool.Mode.PLATFORM);   // unknown -> safe default
    }

    @Test
    void platformAndVirtual_needSizeAtLeastOne() {
        assertThatThrownBy(() -> WorkerPool.create("t", 0, WorkerPool.Mode.PLATFORM))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size >= 1");
        assertThatThrownBy(() -> WorkerPool.create("t", 0, WorkerPool.Mode.VIRTUAL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size >= 1");
    }

    @Test
    void perTask_sizeZero_isUncapped() {
        WorkerPool p = WorkerPool.create("t", 0, WorkerPool.Mode.VIRTUAL_PER_TASK);
        assertThat(p.capacity()).isEqualTo(-1);   // -1 = no bound
        p.shutdown(2);
    }

    @Test
    void perTask_positiveSize_isCapped() {
        WorkerPool p = WorkerPool.create("t", 3, WorkerPool.Mode.VIRTUAL_PER_TASK);
        assertThat(p.capacity()).isEqualTo(3);
        p.shutdown(2);
    }

    @Test
    void submit_runsTheWork_onEveryMode() throws InterruptedException {
        for (WorkerPool.Mode mode : WorkerPool.Mode.values()) {
            WorkerPool p = WorkerPool.create("t-" + mode, 4, mode);
            AtomicInteger ran = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(10);
            for (int i = 0; i < 10; i++) {
                boolean accepted = p.submit(() -> { ran.incrementAndGet(); done.countDown(); });
                assertThat(accepted).isTrue();
            }
            assertThat(done.await(5, TimeUnit.SECONDS)).as("mode=%s all ran", mode).isTrue();
            assertThat(ran.get()).isEqualTo(10);
            p.shutdown(2);
        }
    }

    @Test
    void submit_afterShutdown_returnsFalse() {
        WorkerPool p = WorkerPool.create("t", 2, WorkerPool.Mode.PLATFORM);
        p.shutdown(2);
        boolean accepted = p.submit(() -> { });   // pool is down -> rejected, no permit leaked
        assertThat(accepted).isFalse();
    }
}
