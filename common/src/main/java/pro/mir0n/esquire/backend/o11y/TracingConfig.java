/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/07/2026 mir0n  created: explicit distributed-tracing wiring (v1.2.11 O2). The OTel wiring that
 *                   matters -- the OTLP span exporter (endpoint) and the head sampler -- is declared as
 *                   explicit @Beans here; the Micrometer-Tracing bridge assembles them onto the request
 *                   observations. esqTracedAspect backs @EsqTraced; esqTraceRegistrar hands the shared
 *                   ObservationRegistry to EsqTraceMark. esqObservationGate is ONE ObservationPredicate
 *                   deciding whether an observation is populated -- it governs the esq.* marks
 *                   (esquire.tracing.marks-enabled) and refuses an http.* SERVER observation whose
 *                   request path sits under esquire.tracing.excluded-paths (/actuator), so a health
 *                   probe never builds a span. The nested SecurityObservationsOff (@ConditionalOnClass)
 *                   contributes SecurityObservationSettings.noObservations(): Spring Security's own
 *                   filter-chain / authentication / authorization observations are switched off -- they
 *                   were 8 of 13 spans on a request, and being non-http.* the gate could not reach them,
 *                   so refusing the request span would have promoted them to roots of their own traces.
 *                   Gated by esquire.tracing.enabled (off by default = zero cost);
 *                   management.tracing.enabled mirrors it. Imported per service.
 * 07/09/2026 mir0n  v1.2.11 -- esqOtelResource() @Bean added: the OTel resource carries service.name plus
 *                   service.instance.id (<app>.<instanceNo>); esqRodTraceRegistrar() @Bean registers the
 *                   EsqRodTracer (carrying the esquire.tracing.msg-bus-alive-trace opt-in) into the messaging
 *                   o11y.RodTracerHolder hand-off; esqAsyncTraceRegistrar() @Bean hands the
 *                   ObservationRegistry to EsqAsyncTrace
 */

package pro.mir0n.esquire.backend.o11y;


import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.ArrayList;
import java.util.List;
import pro.mir0n.esquire.messaging.o11y.RodTracerHolder;

// Distributed tracing, wired the Esquire way: the OTel span exporter and the sampler are EXPLICIT
// @Beans (no reliance on management.otlp.* / management.tracing.sampling.* magic values). The gateway
// settles a W3C-shaped Esq-Correlation-ID and stamps it into the traceparent header, so the exported
// spans carry traceId == correlationId and cross-link to the ECS log lines.
@Configuration
@ConditionalOnProperty(name = "esquire.tracing.enabled", havingValue = "true")
public class TracingConfig {

    // OTLP/HTTP traces endpoint of the collector (e.g. http://otel-collector:4318/v1/traces).
    @Value("${esquire.tracing.otlp-endpoint:http://localhost:4318/v1/traces}")
    private String otlpEndpoint;

    // Head sampling ratio [0.0 .. 1.0]; parent-based, so a sampled upstream keeps the whole trace.
    @Value("${esquire.tracing.sampling-ratio:1.0}")
    private double samplingRatio;

    // Fine gate (below the master switch): keep/silence our own esq.* trace marks. Default on.
    @Value("${esquire.tracing.marks-enabled:true}")
    private boolean marksEnabled;

    // Request paths that are NOT Esquire work and must never open a span: the actuator surface, hit by
    // the kubelet/docker health probes several times a minute per instance. Comma-separated prefixes.
    // Filtering here means the span is never CREATED (vs. created, exported, then dropped downstream).
    @Value("${esquire.tracing.excluded-paths:/actuator}")
    private String excludedPaths;

    // Opt-in: trace the RR liveness round-trip (a CLIENT TestRequest and the SERVER HeartBeat reply), so an RR
    // bus's health is observable end-to-end. Off by default -- heartbeats fire every interval, so this is a
    // deliberate choice (pair with sampling if turned on broadly). Only meaningful on RR buses running the
    // alive session; one-way buses ignore it. Handed to the messaging layer on the tracer itself.
    @Value("${esquire.tracing.msg-bus-alive-trace:false}")
    private boolean msgBusAliveTrace;

    // The OTel resource carried by every span this service emits. Boot builds a default (service.name from
    // spring.application.name); we REPLACE it (@ConditionalOnMissingBean on Boot's) to add service.instance.id
    // so the x2 replicas are distinguishable in the trace -- the value is the rod-id token <app>.<instanceNo>,
    // matching the bus "from" attribute. instanceNo() is lazy-cached in EsqUtils (resolved once per JVM). Only
    // contributed when tracing is enabled (this whole config is @ConditionalOnProperty).
    @Bean
    public io.opentelemetry.sdk.resources.Resource esqOtelResource(
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown}") String appName) {
        String instanceId = appName + "." + pro.mir0n.esquire.common.EsqUtils.instanceNo();
        // service.instance.id = the rod-id <app>.<instanceNo>. The o11y collector rewrites service.name to this
        // on the traces pipeline, so the trace waterfall badges each span with its replica (logs / future
        // metrics do not pass through that collector and keep the logical service.name).
        return io.opentelemetry.sdk.resources.Resource.getDefault().toBuilder()
                .put("service.name", appName)
                .put("service.instance.id", instanceId)
                .build();
    }

    // Explicit OTLP exporter -> the collector. Its presence backs off Boot's default OTLP exporter,
    // so the collector endpoint is owned here, not by a management.otlp.tracing.endpoint property.
    @Bean
    public OtlpHttpSpanExporter esqOtlpSpanExporter() {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();
    }

    // Explicit head sampler (parent-based ratio). Its presence backs off Boot's probability sampler,
    // so the sampling posture is owned here, not by management.tracing.sampling.probability.
    @Bean
    public Sampler esqTraceSampler() {
        return Sampler.parentBased(Sampler.traceIdRatioBased(samplingRatio));
    }

    // The single "populate this trace or not" decision point. Both our esq.* marks (from @EsqTraced /
    // EsqTraceMark.around) and the auto-instrumented http.* request spans are Observations on the same
    // registry, so ONE predicate governs them together: name-based, config-driven, no per-annotation
    // flags. Boot applies every ObservationPredicate bean to each ObservationRegistry.
    //
    // An http.* SERVER observation on an excluded path is refused outright, so a health probe never
    // builds a span at all. This is only safe because Spring Security's own observations are switched
    // off (see SecurityObservationsOff below): were they left on, refusing the parent would PROMOTE
    // them to root spans of their own brand-new traces -- and they carry no http.url, so the
    // collector's health-drop rule could not catch them either.
    @Bean
    public ObservationPredicate esqObservationGate() {
        final boolean marks = marksEnabled;
        final List<String> excluded = parsePrefixes(excludedPaths);
        return (name, context) -> {
            boolean ret = true;
            if (name != null) {
                if (name.startsWith("esq.")) {
                    ret = marks;
                } else if (name.startsWith("http.")) {
                    ret = !isExcluded(requestPath(context), excluded);
                }
            }
            return ret;
        };
    }

    // The request path of an inbound (server) observation, or null when the context carries no request
    // -- an outbound http.client.* observation, or anything else. Both the servlet and the reactive
    // ServerRequestObservationContext extend micrometer's ReceiverContext, so the carrier is reached
    // without naming either Spring context type. A null path is never excluded.
    private static String requestPath(Observation.Context context) {
        String ret = null;
        if (context instanceof ReceiverContext<?> receiverContext) {
            Object carrier = receiverContext.getCarrier();
            if (carrier instanceof HttpServletRequest servletRequest) {
                ret = servletRequest.getRequestURI();
            } else if (carrier instanceof ServerHttpRequest reactiveRequest) {
                ret = reactiveRequest.getURI().getPath();
            }
        }
        return ret;
    }

    // A path is excluded when it equals a configured prefix or sits under it (/actuator, /actuator/health).
    private static boolean isExcluded(String path, List<String> prefixes) {
        boolean ret = false;
        if (path != null) {
            for (String prefix : prefixes) {
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    // Comma-separated prefixes -> trimmed, blanks dropped.
    private static List<String> parsePrefixes(String csv) {
        List<String> ret = new ArrayList<>();
        if (csv != null) {
            for (String raw : csv.split(",")) {
                String prefix = raw.trim();
                if (!prefix.isEmpty()) {
                    ret.add(prefix);
                }
            }
        }
        return ret;
    }

    // Backs @EsqTraced on any Spring-managed method: an annotated service method becomes its own span
    // (a child of the request span), so the service transaction shows up as an explicit step in the
    // trace waterfall. Machinery lives here (D6); a service opts in per method with
    // @EsqTraced(name = "esq.svc.<op>", label = "..."). No annotation -> no extra span.
    @Bean
    public EsqTracedAspect esqTracedAspect(ObservationRegistry observationRegistry) {
        return new EsqTracedAspect(observationRegistry);
    }

    // Hand the app's ObservationRegistry to EsqTraceMark so non-Spring / final code (the keep writer, the
    // account transaction processors) can emit the SAME trace marks via EsqTraceMark.around -- the
    // programmatic twin of @EsqTraced that AOP cannot reach.
    @Bean
    public org.springframework.beans.factory.InitializingBean esqTraceRegistrar(ObservationRegistry observationRegistry) {
        return () -> EsqTraceMark.setRegistry(observationRegistry);
    }

    // Hand the OTel-backed bus-hop tracer to the messaging engine (O2/T3) so a trace continues across the
    // messaging bus: the producer stamps a traceparent (trace id = correlationId), the consumer runs its worker
    // inside a span nested under it. Built on the raw OTel Tracer (not the ObservationRegistry) so the two bus
    // legs get explicit span kinds -- PRODUCER on send, CONSUMER on receive -- which a viewer renders as an async
    // messaging pair. Registered ONLY here (tracing enabled) -- when off, the engine keeps IRodTracer.NOOP and the
    // bus pays nothing. The msg-bus-alive-trace opt-in rides on the tracer (IRodTracer.aliveTrace()).
    @Bean
    public org.springframework.beans.factory.InitializingBean esqRodTraceRegistrar(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        return () -> RodTracerHolder.setTracer(
                new EsqRodTracer(openTelemetry.getTracer("pro.mir0n.esquire.o11y.bus"), msgBusAliveTrace));
    }

    // Hand the registry to the async-boundary primitive (O2/T3), so work handed to a queue worker (the enyMan
    // move queue) continues the request's trace on the worker thread. Only registered when tracing is enabled.
    @Bean
    public org.springframework.beans.factory.InitializingBean esqAsyncTraceRegistrar(ObservationRegistry observationRegistry) {
        return () -> EsqAsyncTrace.setRegistry(observationRegistry);
    }

    // Spring Security observes its own filter chain, authentications and authorizations. On an Esquire
    // request that is 8 of 13 spans -- pure framework noise that buries the one span a reader wants (the
    // esq.* mark). Worse, those observations are NOT http.*, so the gate above cannot reach them: with the
    // request span refused on an excluded path they would become root spans of their own traces. Turning
    // them off is therefore what makes the path filter correct as well as what makes a trace readable.
    //
    // Its own @Configuration guarded by @ConditionalOnClass: kcMaster and auKeep carry no
    // spring-security-config, and a @Bean method on TracingConfig itself would force Spring to resolve
    // this return type on every service that imports it (NoClassDefFoundError -- the aspectjweaver trap).
    // A nested class is condition-checked before its methods are ever read.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.config.observation.SecurityObservationSettings")
    static class SecurityObservationsOff {

        @Bean
        public org.springframework.security.config.observation.SecurityObservationSettings esqSecurityObservations() {
            return org.springframework.security.config.observation.SecurityObservationSettings.noObservations();
        }
    }

}
