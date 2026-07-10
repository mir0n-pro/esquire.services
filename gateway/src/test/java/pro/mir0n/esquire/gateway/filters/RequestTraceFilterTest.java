package pro.mir0n.esquire.gateway.filters;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;

import java.util.Collections;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceFilterTest {

    private RequestTraceFilter filter;

    @BeforeEach
    void setUp() {
        // obtainCorrelationId does not use the tracer; a null-yielding provider models tracing being off.
        filter = new RequestTraceFilter(noTracer());
    }

    private static ObjectProvider<Tracer> noTracer() {
        return new ObjectProvider<>() {
            @Override public Tracer getObject() { return null; }
            @Override public Tracer getObject(Object... args) { return null; }
            @Override public Tracer getIfAvailable() { return null; }
            @Override public Tracer getIfUnique() { return null; }
            @Override public Iterator<Tracer> iterator() { return Collections.emptyIterator(); }
        };
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
}
