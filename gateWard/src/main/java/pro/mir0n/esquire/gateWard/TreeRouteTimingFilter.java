/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/15/2026 mir0n  created: the timing points for a tree route answered in process. The gateway's
 *                   InnerTimerFilter and ResponseTraceFilter are GlobalFilters and do not run on a locally
 *                   handled path, so the gw-outer / gw-inner / srv-outer stamps, the capture headers and the
 *                   OUTGOING line are recorded here instead
 * 08/26/2026 mir0n  the meter registry is taken only when the metrics switch is on; the trace ids reach MDC
 */

package pro.mir0n.esquire.gateWard;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * The timing points for a tree route that gateWard answers itself.
 *
 * <p><b>Why this exists.</b> The gate's own timing is split across three beans, and only one of them survives a
 * locally handled request. {@code RequestTraceFilter} is a {@code WebFilter}, so it still runs and still stamps
 * {@link EsqConstants#ESQ_START_TIME}. {@code InnerTimerFilter} and {@code ResponseTraceFilter} are Spring
 * Cloud Gateway {@code GlobalFilter}s: they run inside the routing handler and are never reached when a
 * controller answered instead. Standing apart, the fourth stamp came from bizTree's own servlet
 * {@code MdcFilter}, which is not in this process at all. Left alone, the five tree routes would simply be
 * absent from the latency decomposition -- not wrong, invisible, which is worse.
 *
 * <p><b>The two stamps that matter</b> are taken in {@link BizTreeCacheController} and handed here:
 * <ul>
 *   <li><b>out from the gate</b> -- where the handler gives the work to the cache-read scheduler. This is the
 *       same instant the proxied path measures when it starts the downstream call.</li>
 *   <li><b>in the ward</b> -- where that work actually begins, on the cache-read thread.</li>
 * </ul>
 * Their difference used to be a network round trip and is now the wait for a cache-read thread. It is the same
 * band in the same slot, and it is the number that moves first when the H2 pool saturates -- but it is NOT the
 * same physical quantity, and a dashboard comparing the two topologies must not read the missing wire time as
 * the network having got faster.
 *
 * <p><b>All three bands are recorded together, or the arithmetic breaks.</b> The decomposition subtracts
 * across populations -- {@code in-cluster = gw.inner - srv.outer} and
 * {@code gw self = (gw.outer - gw.inner) - KC} -- so recording an inner band for these routes while the outer
 * band excludes them would drive a band negative. That is why this filter records {@code esq.gw.outer} as well
 * as carrying the other two, and why {@code esq.srv.inner} is recorded as zero rather than skipped: the H2 read
 * is not JPA, so the aspect never had anything to add for a tree read on either topology, and the count has to
 * match {@code esq.srv.outer} for {@code srv self} to close.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // just inside RequestTraceFilter, whose ESQ_START_TIME this reuses
@Component
public class TreeRouteTimingFilter implements WebFilter {

    /** Set by the controller: the gate-to-ward window in ms. Its presence is also what marks a local answer. */
    static final String ATTR_GW_INNER  = "esq.gateward.gwInnerMs";
    /** Set by the controller: the work on the cache-read thread, in ms. */
    static final String ATTR_SRV_OUTER = "esq.gateward.srvOuterMs";
    /** Set here at commit so the header and the meter report the same number. */
    private static final String ATTR_GW_OUTER = "esq.gateward.gwOuterMs";

    /** The route tag for a tree path answered in process. Bounded, and deliberately not "biztree-route": there
     *  is no route. "unknown" is not usable either -- that is what a genuinely unmatched request reports. */
    private static final String ROUTE_TAG = "biztree-local";

    // Non-null only when observability is on (the MeterRegistry bean exists). Resolved once at construction,
    // the same way the gateway's own two timing filters do it.
    private final MeterRegistry registry;

    public TreeRouteTimingFilter(ObjectProvider<MeterRegistry> registryProvider,
                                     @Value("${esquire.observability.metrics.enabled:false}") boolean metricsOn) {
        // The SWITCH decides, not the presence of a bean. A MeterRegistry is NOT absent when the umbrella is
        // off: with the Prometheus export backed off, Boot's SimpleMetricsExportAutoConfiguration supplies a
        // SimpleMeterRegistry, so reading the bean answered "observability is on" in every posture.
        this.registry = metricsOn ? registryProvider.getIfAvailable() : null;
    }

    /** The gate-to-ward window: called by the controller when the cache read has finished. */
    static void gateInner(ServerWebExchange exchange, long outFromGateMs) {
        exchange.getAttributes().put(ATTR_GW_INNER, System.currentTimeMillis() - outFromGateMs);
    }

    /** The work inside the ward: called by the controller on the cache-read thread, in its finally. */
    static void wardOuter(ServerWebExchange exchange, long inTheWardMs) {
        exchange.getAttributes().put(ATTR_SRV_OUTER, System.currentTimeMillis() - inTheWardMs);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Headers have to be written BEFORE the response commits -- a controller writes its body itself, so
        // there is no decorated response to add to afterwards. beforeCommit is the hook that is early enough.
        // The supplier and the runnable below are the two API contracts; neither carries logic of its own.
        exchange.getResponse().beforeCommit(() -> headerHook(exchange));

        Mono<Void> afterAnswer = Mono.fromRunnable(() -> recordAndLog(exchange));
        Mono<Void> ret = chain.filter(exchange).then(afterAnswer);

        return ret;
    }

    /** The beforeCommit contract wants a Mono; the work is a plain call, so this is where the two meet. */
    private Mono<Void> headerHook(ServerWebExchange exchange) {
        writeHeaders(exchange);
        return Mono.empty();
    }

    /** True once the controller has answered from the cache; false for every proxied route and for a 404. */
    private static boolean answeredLocally(ServerWebExchange exchange) {
        boolean ret = exchange.getAttribute(ATTR_GW_INNER) != null;
        return ret;
    }

    /** The load-test instrument: the client asked, and the master switch is on. Same gate as the classic path. */
    private static boolean captureWanted(ServerWebExchange exchange) {
        boolean ret = exchange.getRequest().getHeaders().containsKey(EsqConstants.X_CAPTURE_METRICS)
                   && Boolean.TRUE.equals(exchange.getAttribute(EsqConstants.ESQ_CAPTURE_METRICS));
        return ret;
    }

    /**
     * Adds a header only if it is not already there.
     *
     * <p>The commit hook is not guaranteed to run once. On the error path the response is rendered by the
     * gateway's own exception handler and the hook fires again, which with a plain add leaves the caller
     * holding the header TWICE with two different values. {@code InnerTimerFilter} guards its own add for the
     * same reason; this is that guard, applied to all four.
     */
    private static void addOnce(HttpHeaders headers, String name, String value) {
        if (!headers.containsKey(name)) {
            headers.add(name, value);
        }
    }

    /** How long since a millisecond stamp parked on the exchange, or null when the stamp is not there. */
    private static Long millisSince(ServerWebExchange exchange, String attribute) {
        Long ret = null;
        Long start = exchange.getAttribute(attribute);
        if (start != null) {
            ret = System.currentTimeMillis() - start;
        }
        return ret;
    }

    /**
     * The response side: the correlation ids echoed back, and the four timing headers when the caller asked for
     * them. ResponseTraceFilter does both for a proxied route; neither happens here without this.
     */
    private void writeHeaders(ServerWebExchange exchange) {
        if (answeredLocally(exchange)) {
            HttpHeaders requestHeaders  = exchange.getRequest().getHeaders();
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();

            Long gwOuter = millisSince(exchange, EsqConstants.ESQ_START_TIME);
            if (gwOuter != null) {
                // stored so the meter below reports the same number the caller was given
                exchange.getAttributes().put(ATTR_GW_OUTER, gwOuter);
            }

            String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
            if (requestId != null && !responseHeaders.containsKey(EsqConstants.X_REQUEST_ID)) {
                responseHeaders.add(EsqConstants.X_REQUEST_ID, requestId);
            }
            String correlationId = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
            if (correlationId != null && !responseHeaders.containsKey(EsqConstants.X_CORRELATION_ID)) {
                responseHeaders.add(EsqConstants.X_CORRELATION_ID, correlationId);
            }

            if (captureWanted(exchange)) {
                Long gwInner  = exchange.getAttribute(ATTR_GW_INNER);
                Long srvOuter = exchange.getAttribute(ATTR_SRV_OUTER);
                if (gwOuter != null) {
                    addOnce(responseHeaders, EsqConstants.X_RESPONSE_TIME, gwOuter + "ms");
                }
                if (gwInner != null) {
                    addOnce(responseHeaders, EsqConstants.ESQ_GW_INNER_TIME, gwInner + "ms");
                }
                if (srvOuter != null) {
                    addOnce(responseHeaders, EsqConstants.ESQ_SRV_OUTER_TIME, srvOuter + "ms");
                    // zero, and recorded rather than skipped -- see the class note: the H2 read is not JPA, so
                    // there is no DB time to attribute on either topology, and the band has to stay countable.
                    addOnce(responseHeaders, EsqConstants.ESQ_SRV_INNER_TIME, "0ms");
                }
            }
        }
    }

    /** The meters and the one OUTGOING line, after the answer has gone. */
    private void recordAndLog(ServerWebExchange exchange) {
        if (answeredLocally(exchange)) {
            Long gwOuter  = exchange.getAttribute(ATTR_GW_OUTER);
            Long gwInner  = exchange.getAttribute(ATTR_GW_INNER);
            Long srvOuter = exchange.getAttribute(ATTR_SRV_OUTER);
            if (gwOuter == null) {
                gwOuter = millisSince(exchange, EsqConstants.ESQ_START_TIME);   // no commit hook ran (an error)
            }

            if (registry != null) {
                if (gwOuter != null) {
                    registry.timer("esq.gw.outer", "route", ROUTE_TAG).record(gwOuter, TimeUnit.MILLISECONDS);
                }
                if (gwInner != null) {
                    registry.timer("esq.gw.inner", "route", ROUTE_TAG).record(gwInner, TimeUnit.MILLISECONDS);
                }
                if (srvOuter != null) {
                    registry.timer("esq.srv.outer", "route", ROUTE_TAG).record(srvOuter, TimeUnit.MILLISECONDS);
                    registry.timer("esq.srv.inner", "route", ROUTE_TAG).record(0, TimeUnit.MILLISECONDS);
                }
            }

            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

            String correlationId = exchange.getAttribute(EsqConstants.ESQ_CORRELATION_ID);
            if (correlationId == null) {
                correlationId = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
            }
            String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);

            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);
            if (requestId != null) {
                MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            }
            try {
                log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, srvInnerTime={}, srvOuterTime={}, gwInnerTime={}, gwOuterTime={}ms",
                        correlationId,
                        requestId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI(),
                        exchange.getResponse().getStatusCode() == null ? null : exchange.getResponse().getStatusCode().value(),
                        srvOuter == null ? null : "0ms",
                        srvOuter == null ? null : srvOuter + "ms",
                        gwInner  == null ? null : gwInner + "ms",
                        gwOuter);
            } finally {
                MDC.remove(EsqConstants.PD_CORRELATION_ID);
                MDC.remove(EsqConstants.PD_REQUEST_ID);
            }
        }
    }
}
