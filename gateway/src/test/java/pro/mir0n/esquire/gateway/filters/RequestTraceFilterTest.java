package pro.mir0n.esquire.gateway.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RequestTraceFilterTest {

    private RequestTraceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTraceFilter();
    }

    @Test
    void obtainCorrelationId_esqIdPresent_returnsEsqId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.ESQ_CORRELATION_ID, "esq-111");
        headers.add(EsqConstants.X_CORRELATION_ID, "x-222");

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isEqualTo("esq-111");
    }

    @Test
    void obtainCorrelationId_onlyXCorrelationId_returnsXId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(EsqConstants.X_CORRELATION_ID, "x-222");

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isEqualTo("x-222");
    }

    @Test
    void obtainCorrelationId_noHeaders_returnsGeneratedUuid() {
        HttpHeaders headers = new HttpHeaders();

        String result = filter.obtainCorrelationId(headers);

        assertThat(result).isNotNull();
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }
}
