package pro.mir0n.esquire.backend.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import pro.mir0n.esquire.common.EsqConstants;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemDetailWriterTest {

    // The mill puts an OffsetDateTime on the timestamp property. A mapper without the time module cannot
    // serialize it, and the body written straight to the stream then ends mid-key -- a 401 the caller
    // cannot parse.
    @Test
    void problemWithATimestamp_writesACompleteDocument() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/esq-cmd");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.UNAUTHORIZED, "Unauthorized", "No identity was presented", null);

        ProblemDetailWriter.write(response, problem);

        JsonNode node = new ObjectMapper().readTree(body.toByteArray());
        assertThat(node.path("status").asInt()).isEqualTo(401);
        assertThat(node.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(node.path("instance").asText()).isEqualTo("/esq-cmd");
    }

    @Test
    void theTimestampIsAnIsoString_notAnEpochNumber() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/esq-cmd");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.UNAUTHORIZED, "Unauthorized", "No identity was presented", null);

        ProblemDetailWriter.write(response, problem);

        JsonNode timestamp = new ObjectMapper().readTree(body.toByteArray())
                .path("properties").path(EsqConstants.PD_TIMESTAMP);
        assertThat(timestamp.isTextual()).isTrue();
        assertThat(timestamp.asText()).contains("T");
    }

    @Test
    void theResponseCarriesTheProblemStatusTypeAndLength() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/esq-cmd");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));

        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.FORBIDDEN, "Forbidden", "Not permitted", null);

        ProblemDetailWriter.write(response, problem);

        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
        verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        verify(response).setContentLength(body.size());
    }

    private static ServletOutputStream capture(ByteArrayOutputStream sink) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                sink.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };
    }
}
