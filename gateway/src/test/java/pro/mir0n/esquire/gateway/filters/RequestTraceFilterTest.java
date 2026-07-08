package pro.mir0n.esquire.gateway.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceFilterTest {

    private RequestTraceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTraceFilter();
    }

    @Test
    void obtainCorrelationId_w3cEsqIdPresent_keptUnchanged() {
        String valid = "0123456789abcdef0123456789abcdef";
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, valid);

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isEqualTo(valid);
    }

    @Test
    void obtainCorrelationId_esqIdPresent_takesPrecedenceOverXAndIsConverted() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, "esq-111");
        headers.add(EsqConstants.X_CORRELATION_ID, "x-222");

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isEqualTo(EsqUtils.toW3cTraceId("esq-111"));
        assertThat(EsqUtils.isW3cTraceId(result)).isTrue();
    }

    @Test
    void obtainCorrelationId_onlyXCorrelationId_converted() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.X_CORRELATION_ID, "x-222");

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isEqualTo(EsqUtils.toW3cTraceId("x-222"));
        assertThat(EsqUtils.isW3cTraceId(result)).isTrue();
    }

    @Test
    void obtainCorrelationId_onlyRequestId_generatesFreshNotFromRequestId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.X_REQUEST_ID, "bff-request-42");

        String result = filter.obtainCorrelationId(headers);

        assertThat(EsqUtils.isW3cTraceId(result)).isTrue();
        // the request id is NOT a seed for the correlation id / trace id
        assertThat(result).isNotEqualTo(EsqUtils.toW3cTraceId("bff-request-42"));
    }

    @Test
    void obtainCorrelationId_noHeaders_generatesW3cId() {
        HttpHeaders headers = new HttpHeaders();

        String result = filter.obtainCorrelationId(headers);

        assertThat(EsqUtils.isW3cTraceId(result)).isTrue();
    }

    @Test
    void settleTraceparent_matchingIncoming_keptSoUpstreamSpanStaysParent() {
        String correlationId = "0123456789abcdef0123456789abcdef";
        String incoming = "00-" + correlationId + "-aaaaaaaaaaaaaaaa-01";

        String result = filter.settleTraceparent(incoming, correlationId);

        assertThat(result).isEqualTo(incoming);
    }

    @Test
    void settleTraceparent_incomingTraceIdDiffers_freshTraceparentFromCorrelationId() {
        String correlationId = "0123456789abcdef0123456789abcdef";
        String incoming = "00-ffffffffffffffffffffffffffffffff-aaaaaaaaaaaaaaaa-01";

        String result = filter.settleTraceparent(incoming, correlationId);

        assertThat(result).isNotEqualTo(incoming);
        assertThat(EsqUtils.traceIdFromTraceparent(result)).isEqualTo(correlationId);
    }

    @Test
    void settleTraceparent_noIncoming_mintsFromCorrelationId() {
        String correlationId = "0123456789abcdef0123456789abcdef";

        String result = filter.settleTraceparent(null, correlationId);

        assertThat(EsqUtils.isValidTraceparent(result)).isTrue();
        assertThat(EsqUtils.traceIdFromTraceparent(result)).isEqualTo(correlationId);
    }
}
