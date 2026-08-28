/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/27/2026 mir0n  created: the one place a ProblemDetail reaches a servlet response -- a mapper that can
 *                   serialize the OffsetDateTime the mill sets, and a body built before the response is
 *                   touched
 */

package pro.mir0n.esquire.backend.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

public class ProblemDetailWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ProblemDetailWriter() {}

    public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        //xxx: the body is built BEFORE the response is touched. Serializing into the output stream puts
        //     every token on the wire as it is written, so a value with no serializer leaves a half-body
        //     behind on a response that is already committed and cannot be replaced.
        byte[] body = MAPPER.writeValueAsBytes(problem);
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
