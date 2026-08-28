package pro.mir0n.esquire.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
// The off-request case is the one that bites: RequestPerformance is @RequestScope, so there is no such bean on a
// thread with no request (the taijitu cache loader drives JPA on a monad worker) and nothing to attribute the
// time to. The aspect must detect that and skip -- never proceed to addJpaTime(), which surfaced as a 500.
//
// A request thread is modelled here the way Spring actually defines one: request attributes bound to
// RequestContextHolder. That is the same question the aspect now asks, and the same one the @RequestScope proxy
// answers -- so these tests exercise the real condition, not a mock's willingness to throw.
class PerformanceAspectTest {

    @AfterEach
    void clearRequestScope() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void onARequestThread() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

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
        // THE REGRESSION GUARD. No request attributes are bound -- this is the taijitu cache loader on a monad
        // worker. The aspect must skip, and must NOT reach addJpaTime(), which off-request is a 500.
        // The mock is armed to throw exactly as the real @RequestScope proxy would, so if the aspect ever touches
        // the scoped bean off-request the test blows up instead of quietly passing.
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured())
                .thenThrow(new ScopeNotActiveException("requestPerformance", "request", null));
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()), true);

        ProceedingJoinPoint joinPoint = proceedingTo("rows");

        assertThatCode(() -> assertThat(aspect.trackJpaTime(joinPoint)).isEqualTo("rows"))
                .doesNotThrowAnyException();
        verify(performance, never()).addJpaTime(anyLong());   // nothing to attribute the time to
    }

    @Test
    void offRequestThread_neverTouchesTheScopedBeanAtAll() throws Throwable {
        // The trap is REMOVED, not caught: off-request the aspect asks RequestContextHolder and stops. It must not
        // reach the @RequestScope bean and rely on the throw to find out where it is running. No exception as
        // control flow -- so there is no ordering left in the condition for a future edit to break.
        RequestPerformance performance = mock(RequestPerformance.class);
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()), true);

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance, never()).isMetricsCaptured();
        verify(performance, never()).addJpaTime(anyLong());
    }

    @Test
    void observabilityOn_onARequestThread_timesTheCall() throws Throwable {
        // the DB band is being charted -> the number was asked for
        onARequestThread();
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(false);
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()), true);

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance).addJpaTime(anyLong());
    }

    @Test
    void captureHeaderOn_withObservabilityOff_stillTimesTheCall() throws Throwable {
        // the load-test instrument asked for it, even though the umbrella is off
        onARequestThread();
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(true);
        // The real OFF state: Boot still supplies a SimpleMeterRegistry once the Prometheus export backs off,
        // so the registry is PRESENT and the SWITCH is what says off.
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()), false);

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance).addJpaTime(anyLong());
    }

    @Test
    void nobodyAsked_doesNoTimingWorkAtAll() throws Throwable {
        // on a request, but umbrella off AND no capture header -> the computer does no timing work
        onARequestThread();
        RequestPerformance performance = mock(RequestPerformance.class);
        when(performance.isMetricsCaptured()).thenReturn(false);
        // The real OFF state: Boot still supplies a SimpleMeterRegistry once the Prometheus export backs off,
        // so the registry is PRESENT and the SWITCH is what says off.
        PerformanceAspect aspect = new PerformanceAspect(performance, providerOf(new SimpleMeterRegistry()), false);

        assertThat(aspect.trackJpaTime(proceedingTo("rows"))).isEqualTo("rows");

        verify(performance, never()).addJpaTime(anyLong());
    }
}
