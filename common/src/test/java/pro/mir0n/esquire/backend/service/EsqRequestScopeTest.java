package pro.mir0n.esquire.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EsqRequestScopeTest {

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("the claims are complete with an entity id, a root path and at least one role")
    void claimsComplete() {
        assertThat(EsqRequestScope.claimsComplete("u1", "1.2.", List.of("teller"))).isTrue();
        assertThat(EsqRequestScope.claimsComplete(null, "1.2.", List.of("teller"))).isFalse();
        assertThat(EsqRequestScope.claimsComplete("u1", null, List.of("teller"))).isFalse();
        assertThat(EsqRequestScope.claimsComplete("u1", "1.2.", List.of())).isFalse();
        assertThat(EsqRequestScope.claimsComplete("u1", "1.2.", null)).isFalse();
    }

    @Test
    @DisplayName("bind sets the context with its MDC ids and uid; unbind clears the context and the uid")
    void bindAndUnbind() {
        EsqRequestContext ctx = new EsqRequestContext("c1", "r1", "u1", "1.2.", List.of("teller"));

        EsqRequestScope.bind(ctx);
        assertThat(EsqContextHolder.get()).isEqualTo(ctx);
        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isEqualTo("c1");
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isEqualTo("r1");
        assertThat(MDC.get(EsqConstants.PD_UID)).isEqualTo("u1");

        EsqRequestScope.unbind();
        assertThat(EsqContextHolder.get()).isNull();
        assertThat(MDC.get(EsqConstants.PD_UID)).isNull();
    }

    @Test
    @DisplayName("the OUTGOING line keeps its format: what, status, the service time and the backend time")
    @SuppressWarnings("unchecked")
    void outgoingLine() {
        EsqRequestScope scope = new EsqRequestScope(mock(ObjectProvider.class), false);
        Logger log = mock(Logger.class);
        RequestPerformance performance = new RequestPerformance();

        scope.incoming(log, "POST /esq-acct");
        scope.outgoing(log, "POST /esq-acct", "/esq-acct", 200, 12L, performance);

        verify(log).info("INCOMING: {}", "POST /esq-acct");
        verify(log).info("OUTGOING: {}, Status: {},  Service: {}ms, Backend: {}ms", "POST /esq-acct", 200, 12L, "(n/a)");
    }
}
