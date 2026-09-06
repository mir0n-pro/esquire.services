/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 09/05/2026 mir0n  created: writes a ProblemDetail on a refused route, naming the caller from
 *                   preferred_username, esq_uid or sub, in place of Spring's empty 403
 */
package pro.mir0n.esquire.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.gateway.error.ProblemDetailMill;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class EsqAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final Logger log    = LoggerFactory.getLogger(EsqAccessDeniedHandler.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqAccessDeniedHandler.class.getName());

    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String UNKNOWN_LOGIN = "This login";
    private static final String TITLE = "No privileges";
    private static final String TYPE  = "https://mir0n.pro/errors";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        Mono<String> login = ReactiveSecurityContextHolder.getContext().map(EsqAccessDeniedHandler::loginOf);
        return login.defaultIfEmpty(UNKNOWN_LOGIN).flatMap(name -> write(exchange, name));
    }

    private static String loginOf(SecurityContext context) {
        String ret = UNKNOWN_LOGIN;
        Authentication auth = context.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            Jwt token = (Jwt) auth.getPrincipal();
            String name = token.getClaimAsString(CLAIM_PREFERRED_USERNAME);
            if (name == null || name.isBlank()) {
                name = token.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ID);
            }
            if (name == null || name.isBlank()) {
                name = token.getSubject();
            }
            if (name != null && !name.isBlank()) {
                ret = name;
            }
        }
        return ret;
    }

    private Mono<Void> write(ServerWebExchange exchange, String login) {
        Mono<Void> ret;
        String path = exchange.getRequest().getPath().value();
        log.warn("EsqAccessDeniedHandler: {} refused on {} -- no privileges", login, path);
        devLog.warn("EsqAccessDeniedHandler: {} refused on {} -- the role the route asks for is not held",
                login, path);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            byte[] body = mapper.writeValueAsBytes(problemOf(exchange, login));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            ret = response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("EsqAccessDeniedHandler: the refusal body could not be written: {}", e.getMessage());
            devLog.error("EsqAccessDeniedHandler: the refusal body could not be written", e);
            ret = response.setComplete();
        }
        return ret;
    }

    private static ProblemDetail problemOf(ServerWebExchange exchange, String login) {
        ProblemDetail ret = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                login + " has no privileges in Esquire");
        ret.setTitle(TITLE);
        ret.setType(URI.create(TYPE));
        ret.setInstance(URI.create(exchange.getRequest().getPath().value()));
        ret.setProperty(EsqConstants.PD_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String traceId = EsqUtils.settleCorrelationId(ProblemDetailMill.getCorrelationId(headers));
        ret.setProperty(EsqConstants.PD_TRACE_ID, traceId);
        ret.setProperty(EsqConstants.PD_CORRELATION_ID, traceId);
        String requestId = ProblemDetailMill.getRequestId(headers);
        if (requestId != null) {
            ret.setProperty(EsqConstants.PD_REQUEST_ID, requestId);
        }
        return ret;
    }
}
