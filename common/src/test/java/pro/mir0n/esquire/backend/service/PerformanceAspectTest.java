package pro.mir0n.esquire.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The JPA timing gate: the aspect must time a call ONLY when someone actually asked for the number -- either the
// observability umbrella is on (the esq.srv.inner DB band is being charted) or the request carries the
// X-Capture-Metrics load-test header. Nobody asking = no timing work at all.
//
// The off-request case is the one that bites: RequestPerformance is @RequestScope, so touching it from a thread
// with no active request (the taijitu cache loader drives JPA on a monad worker) throws ScopeNotActiveException.
// The aspect must detect that and skip -- never proceed to addJpaTime(), which would surface as a 500.
class PerformanceAspectTest {

    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry registry) {
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    private static ProceedingJoinPoint proceedingTo(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    @Test
    void offRequestThread_withObservabilityOn_skipsTiming_insteadOfBlowingUp() throws Throwable {
        // REGRESSION GUARD. The scope probe lives inside isMetricsCaptured(); a `observabilityOn || captured`
        // condition SHORT-CIRCUITS it away when observability is on, so the aspect never learns there is no request
        // scope and then calls addJpaTime() off-request -> ScopeNotActiveException -> 500 on any endpoint whose JPA
        // runs on a background thread (biztree's cache loader). The probe must ALWAYS run.
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenThrow(new ScopeNotActiveException("requestPerformance", "request", null));
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()));

        ProceedingJoinPoint joinPoint = proceedingTo("rows");

        assertThatCode(() -> assertThat(aspect.trackJpaTime(joinPoint)).isEqualTo("rows"))
                .doesNotThrowAnyException();
        verify(performance, never()).addJpaTime(anyLong());   // nothing to attribute the time to
    }

    @Test
    void observabilityOn_onARequestThread_timesTheCall() throws Throwable {
        // the DB band is being charted -> the number was asked for
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(false);
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()));

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance).addJpaTime(anyLong());
    }

    @Test
    void captureHeaderOn_withObservabilityOff_stillTimesTheCall() throws Throwable {
        // the load-test instrument asked for it, even though the umbrella is off
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(true);
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(null));   // no registry = umbrella off

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance).addJpaTime(anyLong());
    }

    @Test
    void nobodyAsked_doesNoTimingWorkAtAll() throws Throwable {
        // umbrella off AND no capture header -> a single boolean check, and the computer does nothing else
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(false);
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(null));

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance, never()).addJpaTime(anyLong());
    }
}
