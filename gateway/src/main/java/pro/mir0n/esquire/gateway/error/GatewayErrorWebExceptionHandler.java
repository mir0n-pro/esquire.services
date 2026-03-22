/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/18/2026 mir0n  bypass shouldCaptureException to ProblemDetailMill
 */
//properties:
//spring.webflux.problemdetails.enabled=true
package pro.mir0n.esquire.gateway.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.*;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
public class GatewayErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    private final boolean shouldCaptureException;
    
public GatewayErrorWebExceptionHandler(ErrorAttributes errorAttributes,
           WebProperties webProperties,
           ApplicationContext applicationContext,
           ServerCodecConfigurer configurer,
            boolean shouldCaptureException) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.setMessageReaders(configurer.getReaders());
        this.shouldCaptureException = shouldCaptureException;
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable error = getError(request);
        HttpStatus status = determineHttpStatus(error);
        // 1. Improve the Title and Detail
        String title = "Gateway Error";
        String detail = error.getMessage();

        if (error instanceof java.net.UnknownHostException || error.getMessage().contains("Failed to resolve")) {
            title = "Service Discovery Error";
            detail = "The requested service is currently unreachable or not registered. Please try again later.";
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            title = "Network Timeout";
            detail = "The downstream service took too long to respond.";
        } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            title = "Service Unavailable";
            detail = "Esquire Gateway could not find an active instance of the requested service.";
        } else if (detail != null && detail.contains("[A(")) {
            // Clean the message: Remove Netty DNS jargon like [A(1)]
            title = "Service Unavailable";
            detail = "The service destination could not be resolved.";
        } else {
            detail = null;
        }

        ProblemDetail problem =ProblemDetailMill.createProblemDetail(
            request,
            status,
            title,
            detail,
            shouldCaptureException,
            error
        );

        Long duration = request.exchange().getAttribute(EsqConstants.ESQ_START_TIME);
        if (duration != null) {
            duration = System.currentTimeMillis() - duration;
            problem.setProperty( EsqConstants.PD_PROCESSING_TIME, duration + "ms");
        }

        Object timestamp = request.exchange().getAttribute(EsqConstants.PD_TIMESTAMP);
        if (timestamp == null) {
            //xxx: when not present: use current
            timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        }

        ServerResponse.BodyBuilder bb = ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);

        HttpHeaders headers = request.headers().asHttpHeaders();
        String requestId = ProblemDetailMill.getRequestId(headers);
        if (requestId != null) {
            bb.header(EsqConstants.X_REQUEST_ID, requestId);
        }
        String correlationId = ProblemDetailMill.getCorrelationId(headers);
        if (correlationId != null
        && request.headers().asHttpHeaders().containsKey(EsqConstants.X_CORRELATION_ID) ) {
            bb.header(EsqConstants.X_CORRELATION_ID, correlationId);
        }

        if (duration != null && request.headers().asHttpHeaders().containsKey(EsqConstants.X_CAPTURE_METRICS)) {
            bb.header(EsqConstants.X_RESPONSE_TIME, duration + "ms");
        }
        return bb.bodyValue(problem);
    }

    private HttpStatus determineHttpStatus(Throwable error) {
        // Handle common Gateway/Reactive exceptions
        if (error instanceof org.springframework.web.server.ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        // Default for routing failures or backend down
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

}

