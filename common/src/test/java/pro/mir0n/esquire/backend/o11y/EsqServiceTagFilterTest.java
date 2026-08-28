package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The second identity on a meter: application says which PROCESS, service says which ESQUIRE SERVICE.
// The two properties that matter:
//   1. A CLASSIC SERVICE IS UNCHANGED -- with no IMeterOwner every meter takes the process name, so
//      service == application and every existing board reads exactly as before.
//   2. THE ANSWER COMES FROM THE ID -- the owner is asked once, at registration, with the meter id and
//      nothing else. A per-thread answer would freeze whichever service touched the meter first.
class EsqServiceTagFilterTest {

    private static String serviceTagOf(MeterRegistry registry, String name) {
        Meter meter = registry.find(name).meter();
        return meter == null ? null : meter.getId().getTag(EsqServiceTagFilter.TAG_SERVICE);
    }

    @Test
    void withNoOwner_everyMeterCarriesTheProcessName() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new EsqServiceTagFilter("enyman", null));

        Counter.builder("esq.biz.entity.ops.total").register(registry);
        registry.timer("jvm.gc.pause");

        assertThat(serviceTagOf(registry, "esq.biz.entity.ops.total")).isEqualTo("enyman");
        assertThat(serviceTagOf(registry, "jvm.gc.pause")).isEqualTo("enyman");
    }

    @Test
    void anAttributedMeterTakesTheOwnersAnswer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        IMeterOwner owner = id -> id.getName().startsWith("esq.biz.kc.") ? "kcmaster" : null;
        registry.config().meterFilter(new EsqServiceTagFilter("mesnie", owner));

        Counter.builder("esq.biz.kc.sync.total").register(registry);

        assertThat(serviceTagOf(registry, "esq.biz.kc.sync.total")).isEqualTo("kcmaster");
    }

    @Test
    void anUnattributableMeterFallsBackToTheProcess() {
        MeterRegistry registry = new SimpleMeterRegistry();
        IMeterOwner owner = id -> null;
        registry.config().meterFilter(new EsqServiceTagFilter("mesnie", owner));

        registry.gauge("jvm.memory.used", 1);

        assertThat(serviceTagOf(registry, "jvm.memory.used")).isEqualTo("mesnie");
    }

    @Test
    void aServiceTagAlreadyOnTheIdIsLeftAlone() {
        MeterRegistry registry = new SimpleMeterRegistry();
        IMeterOwner owner = id -> "kcmaster";
        registry.config().meterFilter(new EsqServiceTagFilter("mesnie", owner));

        Counter.builder("esq.biz.test.total").tag(EsqServiceTagFilter.TAG_SERVICE, "keysmith").register(registry);

        assertThat(serviceTagOf(registry, "esq.biz.test.total")).isEqualTo("keysmith");
    }
}
