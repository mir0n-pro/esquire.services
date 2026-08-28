/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/15/2026 mir0n  created: the domain exceptions of a locally answered tree route mapped to the same statuses
 *                   the servlet services give them. Without it they reach the gateway's error handler, which
 *                   knows nothing about them and renders a generic 503 without logging anything
 */

package pro.mir0n.esquire.gateWard;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.error.EmailExistsException;
import pro.mir0n.esquire.backend.error.GenericRuntimeException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.gateway.error.ProblemDetailMill;

import java.util.List;

/**
 * The error side of a tree route answered in process.
 *
 * <p><b>Why it is needed.</b> The cache read raises the ordinary Esquire domain exceptions --
 * {@code BizTreeService} throws {@code ResourceNotFoundException} for {@code /esq}, {@code /esq-tree} and
 * {@code /esq-enode} alike. Standing alone, bizTree turns those into a status through
 * {@code backend.exception.GlobalExceptionHandler}, and that package is one of the four servlet-bound ones
 * this process deliberately does not have. So the exception ran all the way out to the gateway's
 * {@code GatewayErrorWebExceptionHandler}, which has no idea what it is: it rendered its own
 * <i>"could not find an active instance of the requested service"</i> as a <b>503</b> and, because that handler
 * swallows the throwable, logged nothing at all. A caller asking for a node that is not there was told the
 * service was down.
 *
 * <p><b>The statuses are the servlet ones, deliberately.</b> They mirror
 * {@code backend.error.GenericExceptionHandler} branch for branch, so the two topologies answer the same thing
 * for the same cause: 403 for permission denied, 409 for a conflict, 404 for something that is not there, 400
 * for a value that does not make sense. A branch added to one of the two belongs in the other.
 *
 * <p>The advice is bound to {@link BizTreeCacheController} alone. A proxied route never reaches a controller,
 * so its errors keep going to the gateway handler exactly as before.
 *
 * <p>It also puts the logging back. The servlet handler logs the failure on both tiers; the gateway handler
 * logs nothing, which is what made this invisible in the first place.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = BizTreeCacheController.class)
public class BizTreeCacheErrorAdvice {

    private static final org.slf4j.Logger devLog =
            LoggerFactory.getLogger("develop." + BizTreeCacheErrorAdvice.class.getName());

    /** Held, not built per request: the mill wants a ServerRequest and this is what makes one from an exchange. */
    private final List<HttpMessageReader<?>> messageReaders;

    /** Same switch the gateway's own error handler uses, so both render the stack trace on the same terms. */
    private final boolean captureStackTrace;

    public BizTreeCacheErrorAdvice(ServerCodecConfigurer configurer,
                                   @Value("${esquire.gateway.service-metrics.enabled:true}") boolean captureStackTrace) {
        this.messageReaders = configurer.getReaders();
        this.captureStackTrace = captureStackTrace;
    }

    @ExceptionHandler(GenericRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleGenericRuntimeException(GenericRuntimeException ex,
                                                                       ServerWebExchange exchange) {
        ResponseEntity<ProblemDetail> ret;
        HttpStatus status;
        String title;

        if (ex instanceof PermissionDeniedException) {
            status = HttpStatus.FORBIDDEN;
            title  = "Permission Denied";
        } else if (ex instanceof EmailExistsException || ex instanceof DeleteRestrictedException) {
            status = HttpStatus.CONFLICT;
            title  = "Conflict";
        } else if (ex instanceof ResourceNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            title  = "Not Found";
        } else {
            // InvalidValueException -- the bucket the servlet services use
            status = HttpStatus.BAD_REQUEST;
            title  = "Validation Error";
        }

        String path = exchange.getRequest().getPath().value();
        log.error("{}: {} {}: {}", ex.getClass().getSimpleName(), exchange.getRequest().getMethod(), path, ex.getMessage());
        devLog.error("{}: {} {}: {}", ex.getClass().getSimpleName(), exchange.getRequest().getMethod(), path, ex.getMessage(), ex);

        ServerRequest request = ServerRequest.create(exchange, messageReaders);
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(request, status, title, null, captureStackTrace, ex);

        ret = ResponseEntity.status(status).body(problem);

        return ret;
    }
}
