package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// The esq.biz.* business-meter facility: the single entry point every service call site uses, so a domain seam
// names its meter and its tags and nothing else. Two properties matter most and are asserted here:
//   1. OFF IS FREE -- with no registry (observability umbrella off) every call is a no-op, never a throw. A
//      business seam must not have to ask whether observability is on before it can report what it did.
//   2. A GAUGE CANNOT GO NaN -- gauge() delegates to EsqGauge, so the supplier is held strongly. T5 shipped a
//      gauge that read NaN after GC; T8 adds three more (move queue depth, tree nodes, keep queue depth), and
//      they inherit the fix rather than repeat the bug.
class EsqBizMetersTest {

    @AfterEach
    void detachRegistry() {
        EsqBizMeters.setRegistry(null);   // the facility is static -- never leak a registry into the next test
    }

    @Test
    void withNoRegistry_everyCallIsANoOp_andNeverThrows() {
        // the umbrella is off: nothing registered, nothing counted, and above all nothing blows up at a domain seam
        EsqBizMeters.setRegistry(null);

        assertThatCode(() -> {
            EsqBizMeters.count("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "deny");
            EsqBizMeters.time("esq.biz.acct.tx.duration", 1_000_000L, "type", "deposit");
            EsqBizMeters.gauge("esq.biz.move.queue.depth", () -> 7);
        }).doesNotThrowAnyException();
    }

    @Test
    void count_incrementsTheTaggedCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(registry);

        EsqBizMeters.count("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "deny");
        EsqBizMeters.count("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "deny");
        EsqBizMeters.count("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "allow");

        assertThat(registry.counter("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "deny").count())
                .isEqualTo(2.0);
        assertThat(registry.counter("esq.biz.perm.check.total", "cmd", "UPDATE", "result", "allow").count())
                .isEqualTo(1.0);
    }

    @Test
    void time_recordsTheTaggedTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(registry);

        EsqBizMeters.time("esq.biz.acct.tx.duration", 5_000_000L, "type", "transfer");

        assertThat(registry.timer("esq.biz.acct.tx.duration", "type", "transfer").count()).isEqualTo(1L);
        assertThat(registry.timer("esq.biz.acct.tx.duration", "type", "transfer").totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(5.0);
    }

    @Test
    void gauge_registeredBeforeTheRegistryArrives_stillAppears() {
        // THE STARTUP-ORDER GUARD. A gauge is registered once, at start-up of whatever owns the value -- and a
        // bean's @PostConstruct can run BEFORE this facility's registrar does (MoveQueueManager.start() does
        // exactly that). Without the pending-gauge hold, that call finds a null registry, quietly does nothing,
        // and the gauge NEVER EXISTS: a dead panel, no error, nothing in a log. It shipped that way once; this
        // test is why it cannot again. Counters/timers are immune -- they fire at request time, long after start.
        EsqBizMeters.setRegistry(null);
        AtomicInteger depth = new AtomicInteger(5);

        EsqBizMeters.gauge("esq.biz.move.queue.depth", (IntSupplier) depth::get, "queue", "move");   // too early

        MeterRegistry registry = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(registry);                                                          // registrar runs

        assertThat(registry.get("esq.biz.move.queue.depth").tag("queue", "move").gauge().value())
                .as("a gauge asked for before the registry arrived must still be registered once it does")
                .isEqualTo(5.0);
    }

    @Test
    void gauge_readsLive_andSurvivesGarbageCollection() {
        // THE REGRESSION GUARD, inherited from T7 phase A. Micrometer holds a gauge's state object WEAKLY, and
        // here the state object IS the supplier lambda -- nothing else references it. If the facility did not go
        // through EsqGauge (strongReference), the next GC would collect it and the gauge would read NaN.
        MeterRegistry registry = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(registry);
        AtomicInteger depth = new AtomicInteger(3);

        EsqBizMeters.gauge("esq.biz.move.queue.depth", (IntSupplier) depth::get, "queue", "move");

        assertThat(registry.get("esq.biz.move.queue.depth").tag("queue", "move").gauge().value()).isEqualTo(3.0);

        depth.set(11);
        System.gc();
        for (int i = 0; i < 3; i++) {
            byte[] churn = new byte[1024 * 1024];   // give the collector something to actually do
            assertThat(churn).isNotNull();
            System.gc();
        }

        assertThat(registry.get("esq.biz.move.queue.depth").tag("queue", "move").gauge().value())
                .as("gauge must still read the live value after GC, not NaN")
                .isEqualTo(11.0);
    }
}
