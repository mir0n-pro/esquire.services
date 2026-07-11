/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/07/2026 mir0n  created: the Esquire trace-mark annotation (v1.2.11 O2). Put it on a Spring-managed
 *                   service method and the method becomes its own span (a child of the request span) via
 *                   EsqTracedAspect. name = the low-cardinality observation name; label = the span name in
 *                   the trace. The programmatic twin for non-Spring / final code is EsqTrace.mark(); both
 *                   ride the same ObservationRegistry, so ONE gate (ObservabilityConfig's ObservationPredicate)
 *                   and the ESQ_TRACING_ENABLED master switch govern them together with the HTTP spans.
 */

package pro.mir0n.esquire.backend.o11y;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Declarative trace mark for a Spring-managed service method (see EsqTracedAspect).
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EsqTraced {

    // Observation/metric name -- keep it low cardinality (e.g. "esq.svc.save").
    String name();

    // Span name shown in the trace waterfall (e.g. "save entity").
    String label();
}
