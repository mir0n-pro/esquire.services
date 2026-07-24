/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the edge trace-context propagator (v1.2.11 O2/T3). The reactive HTTP server
 *                   observation is built by HttpWebHandlerAdapter, which wraps the WHOLE WebFilter chain and
 *                   extracts the parent trace context through the tracing Propagator BEFORE any WebFilter can
 *                   run -- so RequestTraceFilter (even at HIGHEST_PRECEDENCE) is too late to set the trace id.
 *                   Overriding the Propagator is the framework's own extension point for that extraction: this
 *                   one settles the correlation id (Esq-/X-Correlation-ID, keep-if-W3C / convert / generate)
 *                   and forces it as the server span's trace id, so span traceId == correlationId at the edge
 *                   and the tracer propagates that same id to every downstream service and bus span. Injection
 *                   is plain W3C. Gated by esquire.observability.enabled (backs Boot's default Propagator only then).
 * 07/23/2026 mir0n  v1.2.11 -- the propagator bean is @ConditionalOnProperty(esquire.observability.tracing.enabled,
 *                   matchIfMissing=true) so it does not load in a metrics-only observability config
 */
package pro.mir0n.esquire.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;

import java.util.Collections;
import java.util.List;

// Replaces Boot's W3C Propagator at the gateway so the inbound trace id is the settled correlation id.
// Only contributed when tracing is enabled; downstream services keep the stock W3C propagator and simply
// inherit the id the gateway stamps.
@Configuration
@ConditionalOnProperty(name = "esquire.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class CorrelationPropagatorConfig {

    // @Primary: Boot's otelPropagator is not @ConditionalOnMissingBean, so both beans coexist; the tracing
    // handlers must resolve to this one. The head sampler (esqTraceSampler) is handed in so a fresh root's
    // sampling decision comes from the real, configured sampler -- not a hardcoded flag.
    @Bean
    @Primary
    public Propagator esqCorrelationPropagator(Tracer tracer, Sampler sampler) {
        return new CorrelationPropagator(tracer, sampler);
    }

    static final class CorrelationPropagator implements Propagator {

        private final Tracer tracer;
        private final Sampler sampler;

        CorrelationPropagator(Tracer tracer, Sampler sampler) {
            this.tracer = tracer;
            this.sampler = sampler;
        }

        // ONLY the header inject() writes. Instrumentation clears declared propagation fields from an
        // outgoing request before re-injecting them -- listing the correlation headers here would strip
        // the Esq-Correlation-ID that RequestTraceFilter stamps (an application header, not a trace field).
        @Override
        public List<String> fields() {
            return List.of(EsqConstants.TRACEPARENT);
        }

        // Downstream (client) leg: write the current span context as a W3C traceparent, so the next service
        // inherits traceId == correlationId. Plain W3C -- no correlation logic on the way out.
        @Override
        public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
            if (context != null && carrier != null && setter != null) {
                String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
                setter.set(carrier, EsqConstants.TRACEPARENT,
                        "00-" + context.traceId() + "-" + context.spanId() + "-" + flags);
            }
        }

        // Inbound (server) leg: the trace id is ALWAYS the settled correlation id (authoritative). Two cases:
        //  - a genuine upstream traceparent whose trace id already equals the correlation id -> keep ITS span id
        //    (real parent) and ITS sampling flag;
        //  - a fresh root -> the "parent" is a stand-in for an absent span, so its span id is a fresh RANDOM id
        //    (a span id has no relationship to the trace id -- it must only be a valid, non-zero 16-hex), and the
        //    sampling decision comes from the configured head sampler applied to the trace id.
        // The gateway server span becomes a child of this context, inheriting traceId == correlationId; a fresh
        // root's stand-in parent never appears, so the server span renders as the trace root.
        @Override
        public <C> Span.Builder extract(C carrier, Getter<C> getter) {
            String incoming = EsqUtils.firstNonBlank(
                    getter.get(carrier, EsqConstants.ESQ_CORRELATION_ID),
                    getter.get(carrier, EsqConstants.X_CORRELATION_ID));
            String correlationId = EsqUtils.settleCorrelationId(incoming);
            String traceparent = getter.get(carrier, EsqConstants.TRACEPARENT);
            String parentSpanId;
            boolean sampled;
            if (EsqUtils.isValidTraceparent(traceparent)
                    && correlationId.equals(EsqUtils.traceIdFromTraceparent(traceparent))) {
                String[] parts = traceparent.split("-");   // 00-<traceId>-<spanId>-<flags>
                parentSpanId = parts[2];
                sampled = !"00".equals(parts[3]);
            } else {
                parentSpanId = IdGenerator.random().generateSpanId();
                sampled = sampler.shouldSample(Context.root(), correlationId, "http.server",
                        SpanKind.SERVER, Attributes.empty(), Collections.emptyList())
                        .getDecision() == SamplingDecision.RECORD_AND_SAMPLE;
            }
            TraceContext parent = tracer.traceContextBuilder()
                    .traceId(correlationId)
                    .spanId(parentSpanId)
                    .sampled(sampled)
                    .build();
            return tracer.spanBuilder().setParent(parent);
        }
    }
}
