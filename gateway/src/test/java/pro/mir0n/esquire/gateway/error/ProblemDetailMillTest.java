package pro.mir0n.esquire.gateway.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.ServerRequest;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemDetailMillTest {

    @Mock
    private ServerRequest request;

    @Mock
    private ServerRequest.Headers serverRequestHeaders;

    // ---- getCorrelationId ----

    @Test
    void getCorrelationId_esqHeaderPresent_returnsEsqCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, "esq-123");
        headers.add(EsqConstants.X_CORRELATION_ID, "x-456");

        String result = ProblemDetailMill.getCorrelationId(headers);

        assertThat(result).isEqualTo("esq-123");
    }

    @Test
    void getCorrelationId_onlyXCorrelationId_returnsXCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.X_CORRELATION_ID, "x-456");

        String result = ProblemDetailMill.getCorrelationId(headers);

        assertThat(result).isEqualTo("x-456");
    }

    @Test
    void getCorrelationId_noHeaders_returnsNull() {
        HttpHeaders headers = new HttpHeaders();

        String result = ProblemDetailMill.getCorrelationId(headers);

        assertThat(result).isNull();
    }

    @Test
    void getRequestId_present_returnsId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.X_REQUEST_ID, "req-789");

        String result = ProblemDetailMill.getRequestId(headers);

        assertThat(result).isEqualTo("req-789");
    }

    @Test
    void getRequestId_absent_returnsNull() {
        HttpHeaders headers = new HttpHeaders();

        String result = ProblemDetailMill.getRequestId(headers);

        assertThat(result).isNull();
    }

    // ---- createProblemDetail ----

    private void stubRequest(HttpHeaders headers) {
        when(request.path()).thenReturn("/test");
        when(request.headers()).thenReturn(serverRequestHeaders);
        when(serverRequestHeaders.asHttpHeaders()).thenReturn(headers);
    }

    @Test
    void createProblemDetail_basicCase_setsStatusTitleAndInstance() {
        stubRequest(new HttpHeaders());

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.INTERNAL_SERVER_ERROR, "Err", "Oops", false, null);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Err");
        assertThat(problem.getDetail()).isEqualTo("Oops");
        assertThat(problem.getInstance()).hasToString("/test");
        assertThat(problem.getProperties()).containsKey(EsqConstants.PD_TRACE_ID);
        assertThat(problem.getProperties()).doesNotContainKey(EsqConstants.PD_STACK_TRACE);
    }

    @Test
    void createProblemDetail_withW3cEsqCorrelationId_keepsTraceIdAndCorrelationId() {
        String valid = "0123456789abcdef0123456789abcdef";
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, valid);
        stubRequest(headers);

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.BAD_REQUEST, "Bad", "msg", false, null);

        assertThat(problem.getProperties()).containsEntry(EsqConstants.PD_TRACE_ID, valid);
        assertThat(problem.getProperties()).containsEntry(EsqConstants.PD_CORRELATION_ID, valid);
    }

    @Test
    void createProblemDetail_withNonW3cEsqCorrelationId_settlesToW3cTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, "esq-abc");
        stubRequest(headers);

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.BAD_REQUEST, "Bad", "msg", false, null);

        String settled = EsqUtils.toW3cTraceId("esq-abc");
        assertThat(problem.getProperties()).containsEntry(EsqConstants.PD_TRACE_ID, settled);
        assertThat(problem.getProperties()).containsEntry(EsqConstants.PD_CORRELATION_ID, settled);
        assertThat(EsqUtils.isW3cTraceId(settled)).isTrue();
    }

    @Test
    void createProblemDetail_shouldCaptureTrue_withException_setsStackTrace() {
        stubRequest(new HttpHeaders());

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.INTERNAL_SERVER_ERROR, "Err", null, true, new RuntimeException("boom"));

        assertThat(problem.getDetail()).isEqualTo("boom");
        assertThat(problem.getProperties()).containsKey(EsqConstants.PD_STACK_TRACE);
        assertThat(problem.getProperties().get(EsqConstants.PD_STACK_TRACE)).isNotNull();
        assertThat(problem.getProperties().get(EsqConstants.PD_STACK_TRACE).toString()).isNotEmpty();
    }

    @Test
    void createProblemDetail_shouldCaptureFalse_withException_noStackTrace() {
        stubRequest(new HttpHeaders());

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.INTERNAL_SERVER_ERROR, "Err", null, false, new RuntimeException("boom"));

        assertThat(problem.getProperties()).doesNotContainKey(EsqConstants.PD_STACK_TRACE);
    }
}
