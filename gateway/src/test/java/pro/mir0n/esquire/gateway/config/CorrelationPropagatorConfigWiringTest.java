package pro.mir0n.esquire.gateway.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Wiring coverage for the edge propagator's condition (RE2): CorrelationPropagatorConfig is a PURE-TRACING config
// -- it needs a Tracer AND a Sampler, both of which exist only when the tracing pillar is on. It must therefore be
// gated on esquire.observability.tracing.enabled, exactly like esqTraceSampler / esqOtelResource / esqOtlpSpanExporter
// -- NOT on the master switch. The regression is metrics-only mode (master on, tracing off): the Tracer and Sampler
// are both absent, so if the config still loaded the context would fail with UnsatisfiedDependency at startup.
class CorrelationPropagatorConfigWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorrelationPropagatorConfig.class);

    @Test
    void metricsOnly_tracingDisabled_configNotLoaded_contextStartsWithoutTracerOrSampler() {
        // Master ON, tracing pillar OFF, and NO Tracer/Sampler beans supplied (as in the real metrics-only posture).
        // The config must be skipped -- so the context starts and the propagator is absent. If it were still gated on
        // the master this would fail: the @Bean would try to inject a Tracer + Sampler that do not exist.
        runner.withPropertyValues(
                        "esquire.observability.enabled=true",
                        "esquire.observability.tracing.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(Propagator.class);
                });
    }

    @Test
    void tracingEnabled_configLoaded_propagatorBeanBuilt() {
        // Tracing pillar ON: Tracer + Sampler exist (Boot supplies them; mocked here), so the config loads and
        // contributes the @Primary Propagator.
        runner.withPropertyValues(
                        "esquire.observability.enabled=true",
                        "esquire.observability.tracing.enabled=true")
                .withBean(Tracer.class, () -> mock(Tracer.class))
                .withBean(Sampler.class, () -> mock(Sampler.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Propagator.class);
                });
    }

    @Test
    void bareMaster_tracingKeyMissing_configLoaded() {
        // matchIfMissing=true: a bare master (tracing.enabled key absent) still contributes the propagator -- the
        // sub-switch defaults to the master, so tracing is on and Tracer + Sampler exist in that posture.
        runner.withPropertyValues("esquire.observability.enabled=true")
                .withBean(Tracer.class, () -> mock(Tracer.class))
                .withBean(Sampler.class, () -> mock(Sampler.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Propagator.class);
                });
    }
}
