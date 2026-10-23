/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: the request work every front shares: claimsComplete(), bind() / unbind() of the
 *                   EsqRequestContext with the MDC uid, the INCOMING / OUTGOING lines (written with the caller's
 *                   logger) and the esq.srv.outer / esq.srv.inner meters
 */

package pro.mir0n.esquire.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The work every front does around one request, whatever carries it (the servlet filters for REST, a bus
 * handler for the bus): the claim check, binding the caller's context, the INCOMING / OUTGOING lines and the
 * esq.srv.outer / esq.srv.inner meters.
 *
 * <p>The lines are written with the CALLER's logger, so each keeps the logger name it always had.
 */
@Component
public class EsqRequestScope {

    // Non-null only when observability is on (the MeterRegistry bean exists). Resolved once at construction.
    private final MeterRegistry registry;

    public EsqRequestScope(ObjectProvider<MeterRegistry> registryProvider,
                           @Value("${esquire.observability.metrics.enabled:false}") boolean metricsOn) {
        this.registry = metricsOn ? registryProvider.getIfAvailable() : null;
    }

    /** The claims a service needs from its caller: the entity id, the root path and at least one role. */
    public static boolean claimsComplete(Object uid, Object rootPath, List<String> roles) {
        return uid != null && rootPath != null && roles != null && !roles.isEmpty();
    }

    /** Binds the caller's context to this thread, with its MDC ids and uid. Pair with {@link #unbind}. */
    public static void bind(EsqRequestContext context) {
        EsqContextHolder.set(context);
        if (context.uid() != null) {
            MDC.put(EsqConstants.PD_UID, context.uid());
        }
    }

    public static void unbind() {
        MDC.remove(EsqConstants.PD_UID);
        EsqContextHolder.clear();
    }

    public void incoming(Logger log, String what) {
        log.info("INCOMING: {}", what);
    }

    /** The OUTGOING line and the two meters, tagged by a bounded route. */
    public void outgoing(Logger log, String what, String route, int status, long duration,
                         RequestPerformance performance) {
        if (registry != null) {
            registry.timer("esq.srv.outer", "route", route).record(duration, TimeUnit.MILLISECONDS);
            registry.timer("esq.srv.inner", "route", route).record(performance.getTotalJpaTime(), TimeUnit.MILLISECONDS);
        }
        String jpaTime = performance.isMetricsCaptured() ? String.valueOf(performance.getTotalJpaTime()) : "(n/a)";
        log.info("OUTGOING: {}, Status: {},  Service: {}ms, Backend: {}ms", what, status, duration, jpaTime);
    }
}
