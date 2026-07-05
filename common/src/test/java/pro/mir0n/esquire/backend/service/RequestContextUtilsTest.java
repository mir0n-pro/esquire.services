package pro.mir0n.esquire.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pro.mir0n.esquire.backend.error.MissingRequestIdException;
import pro.mir0n.esquire.common.EsqConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestContextUtilsTest {

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(HttpServletRequest req) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void holderValuesWin_whenContextBound() {
        EsqContextHolder.set(new EsqRequestContext("corr", "req", "uid-9", "1.2."));

        assertThat(RequestContextUtils.getContext()).isNotNull();
        assertThat(RequestContextUtils.getCorrelationId()).isEqualTo("corr");
        assertThat(RequestContextUtils.getRequestId()).isEqualTo("req");
        assertThat(RequestContextUtils.getUid()).isEqualTo("uid-9");
        assertThat(RequestContextUtils.getRootPath()).isEqualTo("1.2.");
    }

    @Test
    void crlReq_fallBackToHeaders_whenNoHolder() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(EsqConstants.ESQ_CORRELATION_ID)).thenReturn("h-corr");
        when(req.getHeader(EsqConstants.X_REQUEST_ID)).thenReturn("h-req");
        bindRequest(req);

        assertThat(RequestContextUtils.getCorrelationId()).isEqualTo("h-corr");
        assertThat(RequestContextUtils.getRequestId()).isEqualTo("h-req");
        // uid / rootPath have no header source -> null without a holder
        assertThat(RequestContextUtils.getUid()).isNull();
        assertThat(RequestContextUtils.getRootPath()).isNull();
    }

    @Test
    void correlationId_usesXCorrelationFallback_whenEsqHeaderAbsent() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(EsqConstants.ESQ_CORRELATION_ID)).thenReturn(null);
        when(req.getHeader(EsqConstants.X_CORRELATION_ID)).thenReturn("x-corr");
        bindRequest(req);

        assertThat(RequestContextUtils.getCorrelationId()).isEqualTo("x-corr");
    }

    @Test
    void requireRequestId_returnsId_whenPresent() {
        EsqContextHolder.set(new EsqRequestContext("corr", "req", "uid-9", "1.2."));
        assertThat(RequestContextUtils.requireRequestId()).isEqualTo("req");
    }

    @Test
    void requireRequestId_throws_whenAbsent() {
        assertThatThrownBy(RequestContextUtils::requireRequestId)
                .isInstanceOf(MissingRequestIdException.class);
    }

    @Test
    void requireRequestId_throws_whenBlank() {
        EsqContextHolder.set(new EsqRequestContext("corr", "  ", "uid-9", "1.2."));
        assertThatThrownBy(RequestContextUtils::requireRequestId)
                .isInstanceOf(MissingRequestIdException.class);
    }

    @Test
    void allNull_whenNoHolderAndNoRequest() {
        assertThat(RequestContextUtils.getContext()).isNull();
        assertThat(RequestContextUtils.getCorrelationId()).isNull();
        assertThat(RequestContextUtils.getRequestId()).isNull();
        assertThat(RequestContextUtils.getUid()).isNull();
        assertThat(RequestContextUtils.getRootPath()).isNull();
    }
}
