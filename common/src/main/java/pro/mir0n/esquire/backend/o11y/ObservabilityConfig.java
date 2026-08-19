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
 * 07/10/2026 mir0n  v1.2.11 O1 -- metrics folded onto the same umbrella (class was TracingConfig): the Prometheus
 *                   registry is Boot-owned (micrometer-registry-prometheus on common's classpath); this config
 *                   contributes only the policy -- esqCommonMetricTags() (MeterFilter: common tag
 *                   application=<spring.application.name> on every meter) and esqHttpLatencyHistogram()
 *                   (MeterFilter: percentile-histogram on http.server.requests so p95 has _bucket series). Gate
 *                   widened to esquire.observability.enabled -- ONE switch for tracing AND metrics.
 * 07/11/2026 mir0n  v1.2.11 O1/T5 -- ONE config namespace: every key moves under esquire.observability.* (the
 *                   five tracing @Values now read esquire.observability.tracing.*; no sibling esquire.tracing.*
 *                   root is left). SUB-SWITCHES under the master, each defaulted so the master alone is enough:
 *                   esqHttpLatencyHistogram() becomes esqLatencyHistograms(), widened to the esq.* latency timers
 *                   as well as http.server.requests and now gated by esquire.observability.metrics
 *                   .histograms-enabled (OPT-IN, default false: the buckets are ~+73% series; off still leaves
 *                   count/sum/max); the nested TomcatByteMetrics and NettyByteMetrics (@ConditionalOnClass, so a
 *                   servlet service takes the first and the reactive gateway the second) are gated by
 *                   esquire.observability.metrics.bandwidth-enabled (default TRUE, cheap): TomcatByteMetrics
 *                   contributes a LOWEST_PRECEDENCE WebServerFactoryCustomizer re-enabling the Tomcat MBean
 *                   registry Boot disables (without it tomcat.global.sent/received never exist), NettyByteMetrics
 *                   a NettyServerCustomizer turning on reactor-netty server metrics with a coarseUri mapper (first
 *                   path segment only -- the raw uri carries entity ids = unbounded cardinality).
 *                   esqRodTraceRegistrar() becomes esqRodObserverRegistrar(): it takes the MeterRegistry too and
 *                   registers ONE EsqRodObserver (trace + meters) into the messaging o11y.RodObserverHolder.
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- esqBizMetersRegistrar() @Bean hands the MeterRegistry to the esq.biz.*
 *                   business-meter facility (EsqBizMeters), gated by the NEW sub-switch
 *                   esquire.observability.metrics.business-enabled (default TRUE). Gating the REGISTRAR is what
 *                   makes off free: no registry reaches the facility, so every esq.biz.* call site in the fleet
 *                   collapses to a null check. esqLatencyHistograms(): esq.biz.* timers ride histograms-enabled
 *                   BY PREFIX (a name list would need editing every time a service gains a timer), and
 *                   http.server.requests moves INTO the gated set -- it was special-cased always-on so p95 would
 *                   work without asking, and that one clause emitted 1,173 bucket series, 20.5% of the entire
 *                   scrape, whether or not anyone ever looked at a percentile. ONE switch now governs every
 *                   bucket in the fleet: off = 4,597 series, on = 10,686. Count/sum/max survive with it off, so
 *                   the average panels stay populated and only the percentile panels go dark
 * 07/17/2026 mir0n  note at the switch: the OTLP exporter is wrapped by Boot's default BatchSpanProcessor
 *                   (bounded queue, drops on overflow, never blocks the request thread) -- the o11y path is not
 *                   a request-failure mode (I53).
 * 08/17/2026 mir0n  v1.2.13 T3.1 -- esqServiceTag() @Bean added (metrics.enabled): an EsqServiceTagFilter
 *                   stamping service=<esquire service> beside application=<process>, taking the running
 *                   service's IMeterOwner through ObjectProvider. No owner -> service == application, which is
 *                   what every classic service reports. The owner is asked with the meter ID and nothing else:
 *                   a MeterFilter runs at REGISTRATION, so a per-request value would freeze whichever service
 *                   touched the meter first
 */

package pro.mir0n.esquire.backend.o11y;


import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
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
import pro.mir0n.esquire.messaging.o11y.RodObserverHolder;

// Observability, wired the Esquire way -- ONE umbrella over the two emit pillars, tracing and metrics, so a
// service never has one without the other. Everything is EXPLICIT @Beans (no reliance on management.otlp.* /
// management.tracing.sampling.* / metrics-export auto-config magic values). Tracing: the OTLP span exporter
// + head sampler, the ONE observation gate, the @EsqTraced aspect, and the bus / async trace registrars --
// the gateway settles a W3C-shaped Esq-Correlation-ID so exported spans carry traceId == correlationId and
// cross-link to the ECS log lines. Metrics: an explicit Prometheus meter registry the free JVM / HTTP / pool
// binders attach to, tagged with the same replica identity as a span. The whole config is gated by the single
// master switch esquire.observability.enabled (off by default = zero cost).
//
// SWITCHES -- one master, then Esquire-vocabulary SUB-switches under it (the msg-bus-alive-trace pattern). A
// sub-switch never names a vendor lever: it says WHAT is wanted, and this config knows HOW to get it on whatever
// embedded server the service happens to run. Nothing outside this file touches Tomcat MBeans or Netty metrics.
//
// EVERYTHING lives UNDER esquire.observability -- the two pillars are peers beneath the one master, and there is
// no esquire.tracing.* namespace sitting outside the umbrella (it existed while tracing was the only pillar; it
// was folded in when metrics arrived, so the tree cannot drift into "the master is here, its knobs are over there").
//
//   esquire.observability.enabled                        MASTER   off  -- gates tracing AND metrics, all of it
//     esquire.observability.tracing.enabled              sub  =MASTER  -- the TRACING pillar on/off (I41). Defaults
//                                                                         to the master, so a bare master keeps it
//                                                                         on. Set OFF to run metrics-only -- the
//                                                                         service-mesh coexistence case: a mesh
//                                                                         (Istio/Linkerd) already traces the wire, so
//                                                                         Esquire tracing is turned off to avoid
//                                                                         double-tracing while the metrics pillar
//                                                                         stays up.
//     esquire.observability.tracing.otlp-endpoint        sub           -- collector endpoint
//     esquire.observability.tracing.sampling-ratio       sub      1.0  -- head sampling
//     esquire.observability.tracing.marks-enabled        sub      ON   -- keep/silence our own esq.* marks
//     esquire.observability.tracing.excluded-paths       sub           -- never open a span for these (/actuator)
//     esquire.observability.tracing.msg-bus-alive-trace  sub      off  -- trace the RR liveness round-trip
//     esquire.observability.metrics.enabled              sub  =MASTER  -- the METRICS pillar on/off (symmetry with
//                                                                         tracing.enabled). Defaults to the master;
//                                                                         set OFF to run traces-only.
//     esquire.observability.metrics.histograms-enabled   sub      off  -- percentile buckets on EVERY latency timer,
//                                                                          http.server.requests INCLUDED. THE
//                                                                          EXPENSIVE ONE, by a distance: buckets are
//                                                                          ~+79% of the whole scrape, and the http
//                                                                          ones ALONE were 20.5% of it (39 buckets
//                                                                          per label-combo). Opt-in. OFF still
//                                                                          leaves count/sum/max, so every AVERAGE
//                                                                          panel stays populated -- the p95 panels
//                                                                          AND the metric->trace exemplars go dark
//                                                                          (an exemplar hangs off a bucket sample),
//                                                                          exactly what you switch this on to see.
//                                                                          o11y-on.bat sets it; a bare master
//                                                                          switch leaves BOTH dark.
//     esquire.observability.metrics.bandwidth-enabled    sub      ON   -- HTTP byte counters. Cheap, so on by
//                                                                          default. ONE knob covering BOTH embedded
//                                                                          servers: it turns on Tomcat's MBean
//                                                                          registry (servlet services) and Reactor
//                                                                          Netty's server metrics (the gateway) --
//                                                                          the caller never needs to know which.
//     esquire.observability.metrics.business-enabled     sub      ON   -- the esq.biz.* domain tier (what a service
//                                                                          DOES, not how it runs). Measured at 1.25%
//                                                                          of series and tens of ns per call, against
//                                                                          work orders of magnitude dearer -- so ON.
//                                                                          Off gates the REGISTRAR, so every esq.biz.*
//                                                                          call site in the fleet becomes a null check
//                                                                          (0.5 ns): nothing allocated, nothing looked
//                                                                          up, no meter created. Tracing, the free
//                                                                          meters and the bus meters all stay on.
//
// PER-PILLAR ENABLE -- how tracing.enabled / metrics.enabled are honoured (I41). Each pillar is gated at TWO layers,
// and the split is deliberate because an Observation is CROSS-PILLAR: one @EsqTraced/http.* Observation yields BOTH a
// span (tracing handler) AND a timer (metrics handler) from the same event. So:
//   - Boot layer (each application.yml): management.tracing.enabled follows tracing.enabled; management.prometheus.
//     metrics.export.enabled follows metrics.enabled. This is the REAL on/off -- it decides whether Boot assembles
//     the tracing bridge (span handler) and the Prometheus registry+scrape. Turn tracing off there and Observations
//     still make their timers; turn metrics off and they still make their spans.
//   - @Bean layer (here): ONLY the PURE-per-pillar beans carry the sub-condition -- the tracing EXPORT beans
//     (esqOtelResource / esqOtlpSpanExporter / esqTraceSampler) by tracing.enabled, and the metrics-only beans
//     (esqCommonMetricTags / esqTagCardinalityCap / esqLatencyHistograms / esqBizMetersRegistrar / the two byte-metric
//     configs) by metrics.enabled. The SHARED observation machinery (esqObservationGate, esqTracedAspect,
//     esqTraceRegistrar, esqAsyncTraceRegistrar, esqRodObserverRegistrar, SecurityObservationsOff) stays at the
//     MASTER level: it feeds both a span and a timer, so gating it to one pillar would silently starve the other
//     (e.g. gating the aspect to tracing would drop the esq.svc.* TIMERS in a metrics-only deployment, and gating the
//     gate would let /actuator health noise back into http.server.requests METRICS). It costs a cheap no-op
//     Observation when its pillar is off, which is the correct trade for keeping the other pillar whole.
// Approach (b): the bus observer is registered at MASTER and takes OpenTelemetry via ObjectProvider with a NO-OP
// fallback -- when tracing is off there is no OpenTelemetry bean, so the PRODUCER/CONSUMER span legs go inert while
// the bus meters keep flowing. (Reverse combo metrics-off/tracing-on: bus meters still record into the unscraped
// registry -- harmless, not free; make the observer pillar-aware only if that ever needs to be zero-cost.)
@Configuration
@ConditionalOnProperty(name = "esquire.observability.enabled", havingValue = "true")
public class ObservabilityConfig {

    // OTLP/HTTP traces endpoint of the collector (e.g. http://otel-collector:4318/v1/traces).
    @Value("${esquire.observability.tracing.otlp-endpoint:http://localhost:4318/v1/traces}")
    private String otlpEndpoint;

    // Head sampling ratio [0.0 .. 1.0]; parent-based, so a sampled upstream keeps the whole trace.
    @Value("${esquire.observability.tracing.sampling-ratio:1.0}")
    private double samplingRatio;

    // Fine gate (below the master switch): keep/silence our own esq.* trace marks. Default on.
    @Value("${esquire.observability.tracing.marks-enabled:true}")
    private boolean marksEnabled;

    // Request paths that are NOT Esquire work and must never open a span: the actuator surface, hit by
    // the kubelet/docker health probes several times a minute per instance. Comma-separated PREFIXES -- each
    // matches itself AND everything under it (/actuator covers /actuator/health); NOT regex, and a prefix list is
    // enough here: the only non-Esquire noise on these API services IS the actuator surface (I34: accept, not regex).
    // Filtering here means the span is never CREATED (vs. created, exported, then dropped downstream).
    @Value("${esquire.observability.tracing.excluded-paths:/actuator}")
    private String excludedPaths;

    // Opt-in: trace the RR liveness round-trip (a CLIENT TestRequest and the SERVER HeartBeat reply), so an RR
    // bus's health is observable end-to-end. Off by default -- heartbeats fire every interval, so this is a
    // deliberate choice (pair with sampling if turned on broadly). Only meaningful on RR buses running the
    // alive session; one-way buses ignore it. Handed to the messaging layer on the tracer itself.
    @Value("${esquire.observability.tracing.msg-bus-alive-trace:false}")
    private boolean msgBusAliveTrace;

    // --- Metrics (O1): common tags, same umbrella as tracing ---
    // The Prometheus registry, the free standard binders (jvm.*, http.server.requests, hikaricp.*, executor.*,
    // logback.events, tomcat.*) and the /actuator/prometheus endpoint are assembled by Boot when
    // management.prometheus.metrics.export.enabled is on (it mirrors the master switch) -- the metrics analog of
    // the Micrometer-Tracing bridge assembling the OTel beans onto the registry. Letting Boot own the registry
    // is deliberate: an explicit PrometheusMeterRegistry bean writes to its OWN prometheus client registry while
    // the scrape endpoint reads Boot's, so the scrape comes back empty. What we OWN explicitly is the POLICY:
    // ONE common tag application=<service> stamped on every meter (Boot applies each MeterFilter bean to every
    // registry), so PromQL filters across replicas by service ({application="enyman"}). The replica itself is
    // Prometheus's own instance=<host:port> per scrape target (a distinct target per k8s pod), so no instance
    // tag is added here -- it would collide with that reserved label.
    //
    // I40 (COVERED 2026-07-15, reaffirmed 07-16): MEASURED is not the same as SPANNED. I40 was filed as "no DB
    // instrumentation" -- that is FALSE. The SYNC in-request DB/SQL timing is ALREADY captured and DRAWN as
    // "srv inner (db, capture-gated)"; what is deliberately absent is only the per-STATEMENT OTel SPAN in the
    // trace waterfall. Do NOT re-file this as "the DB calls are not measured".
    // The primary measure is the esq.srv.inner timer (MdcFilter records performance.getTotalJpaTime() -- the
    // per-request total JPA/DB time, tagged by route; PerformanceAspect captures the repository/DB time). It is
    // "capture-gated": recorded ONLY while observability is on, else simply not emitted (historically the
    // X-Capture-Metrics load-test header gated it; now steady-state under the o11y umbrella). It renders as the
    // innermost band on the "Request latency bands" panels ("srv inner (db)"). Two more DB-timing views back it:
    // hikaricp.connections.usage -> "DB query rate (connection borrows/s)" = rate(_count) and "Avg DB connection
    // hold time (ms/borrow)" = _sum/_count (per-query duration); and esq.biz.keep.write.duration for THE write at
    // the keep sink. What we do NOT add: opentelemetry-jdbc / datasource-proxy per-STATEMENT spans. For a design
    // where reads are served from the in-memory bizTree cache (a request is a cache HIT -- zero SQL; JPA runs
    // only at bulk cache-load in BizTreeCacheLoader, off the request path) and writes are ONE self-contained SQL
    // per repository method (DB-layer rule, no dynamic SQL), no single trace hides an N+1 fan-out a per-statement
    // span would reveal -- "srv inner (db)" already answers how long the DB took per request. A per-statement span
    // would add a dependency + span-per-SQL volume + SQL-text cardinality for no hidden depth. (I39 is the same
    // sentence about the ASYNC kcMaster->KC hop -- also COVERED: its duration is measured by
    // esq.biz.kc.sync.duration, only the per-CALL span is absent.) Revisit ONLY if a DB-heavy,
    // many-queries-per-request path is ever introduced.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter esqCommonMetricTags(@Value("${spring.application.name:unknown}") String appName) {
        return MeterFilter.commonTags(java.util.List.of(Tag.of("application", appName)));
    }

    // The SECOND identity, beside application (T3.1): application = which PROCESS, service = which ESQUIRE
    // SERVICE. A composed process (Mesnie, gateWard) runs more than one service, and a meter's tags are fixed
    // when the meter is REGISTERED -- so the answer comes from the meter id (its name, its route, its bus),
    // never from the calling thread. The running service contributes at most one IMeterOwner; with none, every
    // meter takes the process name and service == application, which is what every classic service does.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter esqServiceTag(@Value("${spring.application.name:unknown}") String appName,
                                     org.springframework.beans.factory.ObjectProvider<IMeterOwner> meterOwner) {
        return new EsqServiceTagFilter(appName, meterOwner.getIfAvailable());
    }

    // I25: cap the distinct VALUES any esq.biz.* / messaging.* tag may take, so an unbounded tag -- an exception
    // message, an entity id, a correlationId reaching a call site (the I6 class) -- can NEVER explode the series
    // count. Past the cap a new value collapses to a sentinel, so the blast radius of the mistake is one extra
    // series, not thousands (and the sentinel is loud, so o11y-verify still flags it). The runtime PREVENTION that
    // pairs with o11y-verify's cardinality DETECTION (I21). 100 is far above any real Esquire tag (the widest is a
    // couple dozen) and far below a leak -- so legitimate meters are never touched, only a runaway is contained.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter esqTagCardinalityCap() {
        return new EsqTagCardinalityCap(100);
    }

    // Publish latency-histogram buckets so a Prometheus histogram_quantile (p95/p99) has _bucket series to read --
    // by default a Boot timer emits only _count / _sum, and the quantile comes back empty (O1/T5 part B). Two tiers:
    //   - http.server.requests: ALWAYS on -- the REST p95 dashboard panel depends on it (T4), and its cardinality
    //     is bounded (uri / method / status).
    //   - the extra latency timers (the Hikari borrow/acquire timers, the bus send-duration, and the four request
    //     timing bands esq.gw.*/esq.srv.*): buckets are added ONLY when esquire.observability.metrics.histograms-
    //     enabled=true, because each is tagged (bus-id / slot / msgType, or the band label) and the le buckets
    //     multiply that cardinality. Off by default = the timers still emit count/sum/max (avg is queryable); on =
    //     full percentiles.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter esqLatencyHistograms(
            @Value("${esquire.observability.metrics.histograms-enabled:false}") boolean histogramsEnabled) {
        java.util.Set<String> gated = java.util.Set.of(
                "http.server.requests",
                "hikaricp.connections.usage", "hikaricp.connections.acquire", "messaging.send.duration",
                "esq.gw.outer", "esq.gw.inner", "esq.srv.outer", "esq.srv.inner");
        return new MeterFilter() {
            @Override
            public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {
                io.micrometer.core.instrument.distribution.DistributionStatisticConfig ret = config;
                String n = id.getName();
                // esq.biz.* timers ride the same sub-switch BY PREFIX -- the business meters are added per
                // service (T8), so listing each name here would mean editing this filter every time a service
                // gains a timer, and forgetting to would silently cost it its percentiles.
                //
                // http.server.requests is in the `gated` set like everything else. It used to be special-cased
                // ALWAYS ON (so p95 worked without asking), and that one clause cost 20.5% OF THE ENTIRE SCRAPE
                // -- 1,173 bucket series, 39 buckets per label-combo, on by default, whether or not anyone ever
                // looked at a percentile. ONE switch now governs every bucket in the fleet: histograms off means
                // no buckets anywhere, and the p95 panels are dark until you ask for them. That is the honest
                // trade, and it is the same one the bus / gateway p95 panels already made.
                boolean gatedName = gated.contains(n) || n.startsWith("esq.biz.");
                if (histogramsEnabled && gatedName) {
                    ret = io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            // it is not needed by Micrometer's design -- exemplars attach to THESE histogram buckets
                            // automatically (the tracing bridge's SpanContextSupplier); there is no per-meter flag.
                            // .exemplarsEnabled(true)
                            .build()
                            .merge(config);
                }
                return ret;
            }
        };
    }

    // The OTel resource carried by every span this service emits. Boot builds a default (service.name from
    // spring.application.name); we REPLACE it (@ConditionalOnMissingBean on Boot's) to add service.instance.id
    // so the x2 replicas are distinguishable in the trace -- the value is the rod-id token <app>.<instanceNo>,
    // matching the bus "from" attribute. instanceNo() is lazy-cached in EsqUtils (resolved once per JVM). Only
    // contributed when observability is enabled (this whole config is @ConditionalOnProperty).
    // Pure TRACING (span resource) -- gated by the tracing pillar; no metrics role.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public io.opentelemetry.sdk.resources.Resource esqOtelResource(
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown}") String appName) {
        String instanceId = appName + "." + pro.mir0n.esquire.common.EsqUtils.instanceNo();
        // service.instance.id = the rod-id <app>.<instanceNo>. The o11y collector rewrites service.name to this
        // on the traces pipeline, so the trace waterfall badges each span with its replica (logs / metrics do
        // not pass through that collector and keep the logical service.name).
        return io.opentelemetry.sdk.resources.Resource.getDefault().toBuilder()
                .put("service.name", appName)
                .put("service.instance.id", instanceId)
                .build();
    }

    // Explicit OTLP exporter -> the collector. Its presence backs off Boot's default OTLP exporter,
    // so the collector endpoint is owned here, not by a management.otlp.tracing.endpoint property.
    // Pure TRACING (span export) -- gated by the tracing pillar.
    //
    // The o11y path CANNOT become a request-failure mode (I53 REJECT). Boot wraps this exporter in its default
    // BatchSpanProcessor -- a BOUNDED queue that DROPS spans on overflow and never blocks the request thread (we
    // set no SimpleSpanProcessor and no queue override, so the SDK default stands). Metrics are PULL (Prometheus
    // scrapes /actuator/prometheus on its own 15s clock, cost O(series) with bounded-cardinality tags -- request
    // rate cannot flood it). Logs go to stdout, tailed out-of-process by Alloy -- a slow Loki backpressures Alloy,
    // not the app. Proven empirically: 3.6M requests including o11y FULL at 100% host CPU -> 0 request failures in
    // the full-stack arms (doc/review/Esquire.PerfMatrix-07-17.md). o11y COSTS throughput under load (24-27%,
    // measured in I49) but never FAILS a request; there is nothing to shed that is not already shed.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public OtlpHttpSpanExporter esqOtlpSpanExporter() {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();
    }

    // Explicit head sampler (parent-based ratio). Its presence backs off Boot's probability sampler,
    // so the sampling posture is owned here, not by management.tracing.sampling.probability.
    // Pure TRACING (sampling posture) -- gated by the tracing pillar.
    @Bean
    @ConditionalOnProperty(name = "esquire.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
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

    // Hand the ONE bus-hop observer to the messaging engine so trace AND metrics continue across the bus (O2/T3
    // trace + O1/T5 meters, under one umbrella). Trace: the producer stamps a traceparent (trace id =
    // correlationId), the consumer runs its worker inside a span nested under it; built on the raw OTel Tracer
    // (not the ObservationRegistry) so the two legs get explicit span kinds (PRODUCER / CONSUMER). Meters: the
    // same object carries the MeterRegistry and emits the messaging.* meters at the send / receive / retry seams.
    // Registered ONLY here (observability enabled) -- when off, the engine keeps IRodObserver.NOOP and the bus
    // pays nothing. The msg-bus-alive-trace opt-in rides on the observer (IRodTracer.aliveTrace()).
    @Bean
    public org.springframework.beans.factory.InitializingBean esqRodObserverRegistrar(
            org.springframework.beans.factory.ObjectProvider<io.opentelemetry.api.OpenTelemetry> openTelemetry,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        // Approach (b), per-pillar enable (I41): this observer carries BOTH pillars (bus trace + bus meters), so it is
        // registered at MASTER level, NOT pillar-gated. When tracing.enabled is off there is NO OpenTelemetry bean
        // (Boot never assembled the tracing bridge), so fall back to OpenTelemetry.noop() -- the PRODUCER/CONSUMER span
        // legs go inert while the bus meters keep flowing off the MeterRegistry (which always exists). ObjectProvider
        // is what makes this safe either way: bean present -> real tracer; bean absent -> no-op, no startup failure.
        io.opentelemetry.api.OpenTelemetry otel = openTelemetry.getIfAvailable(io.opentelemetry.api.OpenTelemetry::noop);
        return () -> RodObserverHolder.setObserver(
                new EsqRodObserver(otel.getTracer("pro.mir0n.esquire.o11y.bus"), meterRegistry, msgBusAliveTrace));
    }

    // Hand the registry to the async-boundary primitive (O2/T3), so work handed to a queue worker (the enyMan
    // move queue) continues the request's trace on the worker thread. Only registered when observability is enabled.
    @Bean
    public org.springframework.beans.factory.InitializingBean esqAsyncTraceRegistrar(ObservationRegistry observationRegistry) {
        return () -> EsqAsyncTrace.setRegistry(observationRegistry);
    }

    // Hand the registry to the esq.biz.* business-meter facility (O1/T8). Registered ONLY here (observability
    // enabled), so with the umbrella off EsqBizMeters holds no registry and every business call site is a null
    // check. Static + registrar for the same reason EsqTraceMark is: the domain seams include objects the
    // container never builds (AcctTransactionProcessor*, the taijitu Monad, KeepSqlStore are new-ed and never
    // proxied), and constructor injection cannot reach those without inventing plumbing to carry a registry in.
    //
    // SUB-SWITCH esquire.observability.metrics.business-enabled (default TRUE). Gating the REGISTRAR is what
    // makes "off" actually free: no registry reaches the facility, so every esq.biz.* call site anywhere in the
    // fleet collapses to a volatile read and a null check (measured: 0.5 ns, vs tens of ns when on). Nothing is
    // allocated, nothing is looked up, no meter exists. That is the lever for a run where the hot path must not
    // pay for the domain tier at all -- the free JVM/HTTP/pool meters, the bus meters and tracing all stay on.
    @Bean
    @ConditionalOnProperty(name = {"esquire.observability.metrics.enabled",
                                   "esquire.observability.metrics.business-enabled"},
                           havingValue = "true", matchIfMissing = true)
    public org.springframework.beans.factory.InitializingBean esqBizMetersRegistrar(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return () -> EsqBizMeters.setRegistry(meterRegistry);
    }

    // Spring Security observes its own filter chain, authentications and authorizations. On an Esquire
    // request that is 8 of 13 spans -- pure framework noise that buries the one span a reader wants (the
    // esq.* mark). Worse, those observations are NOT http.*, so the gate above cannot reach them: with the
    // request span refused on an excluded path they would become root spans of their own traces. Turning
    // them off is therefore what makes the path filter correct as well as what makes a trace readable.
    //
    // Its own @Configuration guarded by @ConditionalOnClass: kcMaster and auKeep carry no
    // spring-security-config, and a @Bean method on ObservabilityConfig itself would force Spring to resolve
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

    // Bandwidth (O1/T5-C): the tomcat.global.* byte counters (sent / received) are read by Micrometer's Tomcat
    // binder off the GlobalRequestProcessor MBean -- but Boot DISABLES Tomcat's MBean registry by default
    // (TomcatServletWebServerFactory.setDisableMBeanRegistry(true)), so the binder finds nothing and only the
    // tomcat.sessions.* meters ever appear. Re-enable the registry HERE, from the observability config itself:
    // this class exists only when the umbrella is on, so the registry -- and its cost -- turns on and off WITH
    // observability, and no service has to repeat the setting in its own application.yml (D6: the machinery lives
    // in common; per-service stays thin).
    // Ordered LAST so it runs AFTER Boot's TomcatWebServerFactoryCustomizer, which would otherwise disable it.
    // Nested + @ConditionalOnClass (string form) for the same reason as above: the reactive gateway has no servlet
    // Tomcat, and a nested class is condition-checked before its methods -- and their return types -- are read.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory")
    @ConditionalOnProperty(name = {"esquire.observability.metrics.enabled",
                                   "esquire.observability.metrics.bandwidth-enabled"},
                           havingValue = "true", matchIfMissing = true)
    static class TomcatByteMetrics {

        @Bean
        @org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
        public org.springframework.boot.web.server.WebServerFactoryCustomizer<
                org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory> esqTomcatMBeanRegistry() {
            return factory -> factory.setDisableMBeanRegistry(false);
        }
    }

    // Bandwidth at the EDGE (O1/T5-C): the gateway runs on Netty (Spring Cloud Gateway), which has no Tomcat MBean,
    // so the Tomcat byte counters above cannot see the client-facing traffic -- the very traffic that matters most.
    // Reactor Netty keeps its own server metrics; switching them on publishes reactor.netty.http.server.data.sent /
    // .received (bytes) into Micrometer's GLOBAL registry, which Boot binds to this service's registry, so they are
    // scraped with everything else. Same umbrella gating as the rest of this class: on only when observability is.
    // Nested + @ConditionalOnClass on the reactor-netty class (string form): the servlet services have no Netty
    // server, and the nested class is condition-checked before its methods -- and their return types -- are read.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "reactor.netty.http.server.HttpServer")
    @ConditionalOnProperty(name = {"esquire.observability.metrics.enabled",
                                   "esquire.observability.metrics.bandwidth-enabled"},
                           havingValue = "true", matchIfMissing = true)
    static class NettyByteMetrics {

        @Bean
        public org.springframework.boot.web.embedded.netty.NettyServerCustomizer esqNettyByteMetrics() {
            return httpServer -> httpServer.metrics(true, NettyByteMetrics::coarseUri);
        }

        // Reactor Netty tags every sample with the RAW uri, which carries entity ids -- unbounded cardinality.
        // Collapse it to the first path segment (/api/esq?id=14 -> /api), so the byte series stay a handful while
        // still separating /api from /auth traffic.
        private static String coarseUri(String uri) {
            String ret = "/root";
            if (uri != null && uri.length() > 1) {
                int cut = uri.indexOf('/', 1);
                ret = (cut > 0) ? uri.substring(0, cut) : uri;
                int query = ret.indexOf('?');
                if (query > 0) {
                    ret = ret.substring(0, query);
                }
            }
            return ret;
        }
    }

}
