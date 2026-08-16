/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/15/2026 mir0n  created: the five tree routes answered IN PROCESS from the cache instead of proxied to
 *                   bizTree -- the same director calls the bizTree controller makes, each handed to the
 *                   cache-read scheduler so no blocking JDBC lands on an event-loop thread, and each carrying
 *                   the caller's rootPath / uid across that thread hop. scoped() also takes the two timing
 *                   stamps (out from the gate, in the ward) for TreeRouteTimingFilter
 */

package pro.mir0n.esquire.gateWard;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * The gate answering the tree routes from the cache in its own memory.
 *
 * <p>Standing apart, these five paths are a {@code biztree-route} entry in the gateway's route table and an
 * HTTP hop to bizTree's own controller. Composed, the route comes out of the table and these handlers take
 * its place -- the SAME {@link IBizTreeDirector} calls that controller makes, so the answers are the cache's,
 * not a second implementation of them.
 *
 * <p><b>Every call is handed to the cache-read scheduler.</b> A read is blocking JDBC into H2; see
 * {@link CacheReadScheduler} for why it must not run on the event loop.
 *
 * <p><b>And every call carries the caller across that thread hop.</b> The director's read methods take no
 * rootPath: they fetch it from {@code RequestContextUtils}, which reads a THREAD-LOCAL. In the standalone
 * service a servlet filter fills it on the request thread. Here the work runs on a scheduler thread instead,
 * so the caller is read from the JWT on the request side and set INSIDE the scheduled call -- the only place
 * that actually executes on the thread the director will use -- then cleared in a finally. Without it the
 * reads still answer, but scoped to nobody: subtree and children come back empty, which reads like an empty
 * cache rather than a missing caller.
 *
 * <p><b>Authentication has already happened.</b> The reactive security chain is a WebFilter on the server, so
 * it runs for every request entering this process -- a locally handled route no less than a proxied one.
 */
@Slf4j
@RestController
public class BizTreeCacheController {

    private static final org.slf4j.Logger devLog =
            LoggerFactory.getLogger("develop." + BizTreeCacheController.class.getName());

    private final IBizTreeDirector director;
    private final Scheduler cacheRead;

    public BizTreeCacheController(IBizTreeDirector director,
                                  @Qualifier("cacheReadScheduler") Scheduler cacheRead) {
        this.director = director;
        this.cacheRead = cacheRead;
    }

    @GetMapping("/esq")
    public Mono<ResponseEntity<List<EsqTreeNode>>> esquire(
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "skip", required = false) Integer skip,
            @RequestParam(name = "take", required = false) Integer take,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = EsqConstants.X_REQUEST_ID, required = false) String requestId,
            @RequestHeader(name = EsqConstants.X_CORRELATION_ID, required = false) String correlationId,
            ServerWebExchange exchange) {
        return onCache(exchange, context(jwt, requestId, correlationId),
                () -> director.esquire(id, skip, take), "esquire", id);
    }

    @GetMapping("/esq-tree")
    public Mono<ResponseEntity<List<EsqTreeNode>>> esquireSubtree(
            @RequestParam(name = "id", required = true) String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = EsqConstants.X_REQUEST_ID, required = false) String requestId,
            @RequestHeader(name = EsqConstants.X_CORRELATION_ID, required = false) String correlationId,
            ServerWebExchange exchange) {
        return onCache(exchange, context(jwt, requestId, correlationId),
                () -> director.esquireSubtree(id), "esquireSubtree", id);
    }

    @GetMapping("/esq-path")
    public Mono<ResponseEntity<List<String>>> esquirePath(
            @RequestParam(name = "id", required = true) String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = EsqConstants.X_REQUEST_ID, required = false) String requestId,
            @RequestHeader(name = EsqConstants.X_CORRELATION_ID, required = false) String correlationId,
            ServerWebExchange exchange) {
        return onCache(exchange, context(jwt, requestId, correlationId),
                () -> director.esquirePath(id), "esquirePath", id);
    }

    @GetMapping("/esq-enode")
    public Mono<ResponseEntity<EsqTreeNode>> esquireEntityNode(
            @RequestParam(name = "kind", required = true) Integer kind,
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "name", required = false) String name,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = EsqConstants.X_REQUEST_ID, required = false) String requestId,
            @RequestHeader(name = EsqConstants.X_CORRELATION_ID, required = false) String correlationId,
            ServerWebExchange exchange) {
        return onCacheNode(exchange, context(jwt, requestId, correlationId), kind, id, name);
    }

    /**
     * Forces a night-watch sweep and answers at once (202) -- the sweep runs on the director's own thread, as
     * it does standing alone, so this one needs neither the read scheduler nor a caller context.
     *
     * <p>It is also the one tree path with no timing points, deliberately: there is no ward window to pair a
     * gate window with, and recording half a pair would put an unmatched count into a band the panel computes
     * by subtraction. All four bands or none, per request -- {@link TreeRouteTimingFilter}.
     */
    @PostMapping("/esq-sweep")
    public Mono<ResponseEntity<Void>> sweep() {
        director.sweepAsync();
        devLog.debug("sweep: forced via REST (async)");
        return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
    }

    /** The caller, taken from the verified token on the request side while the JWT is still in hand. */
    private EsqRequestContext context(Jwt jwt, String requestId, String correlationId) {
        String uid      = (jwt != null) ? jwt.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ID) : null;
        String rootPath = (jwt != null) ? jwt.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH) : null;

        EsqRequestContext ret = new EsqRequestContext(correlationId, requestId, uid, rootPath);

        return ret;
    }

    /** One shape for the three list reads: scope it, run it on the cache scheduler, answer 200. */
    private <T> Mono<ResponseEntity<List<T>>> onCache(ServerWebExchange exchange, EsqRequestContext ctx,
                                                      Callable<List<T>> call, String what, String id) {
        // OUT FROM THE GATE -- the instant the work leaves the gate for the ward. On a proxied route this is
        // where the downstream call starts and InnerTimerFilter takes it; here the hop is a thread, not a wire.
        long outFromGate = System.currentTimeMillis();

        Mono<List<T>> read = Mono.fromCallable(() -> scoped(exchange, outFromGate, ctx, call))
                .subscribeOn(cacheRead);

        // Only build the debug step when someone is reading it. The consumer captures what/id, so it is a new
        // object on EVERY request -- and with the develop log off it would be an object built to do nothing.
        if (devLog.isDebugEnabled()) {
            read = read.doOnNext(list -> devLog.debug("{}: id:{}, count:{}", what, id, list.size()));
        }

        Mono<ResponseEntity<List<T>>> ret = read.map(list -> ResponseEntity.status(HttpStatus.OK).body(list));

        return ret;
    }

    /** The same shape for the single-node read, which answers 200 with no body when there is no such node. */
    private Mono<ResponseEntity<EsqTreeNode>> onCacheNode(ServerWebExchange exchange, EsqRequestContext ctx,
                                                          Integer kind, String id, String name) {
        long outFromGate = System.currentTimeMillis();

        Callable<EsqTreeNode> call = () -> director.esquireEntityNode(kind, id, name);
        Mono<EsqTreeNode> read = Mono.fromCallable(() -> scoped(exchange, outFromGate, ctx, call))
                .subscribeOn(cacheRead);

        if (devLog.isDebugEnabled()) {
            read = read.doOnNext(node -> devLog.debug("esquireEntityNode: kind:{}, id:{}, name:{}", kind, id, name));
        }

        Mono<ResponseEntity<EsqTreeNode>> ret = read
                .map(node -> ResponseEntity.status(HttpStatus.OK).body(node))
                // the node may legitimately not be there; answer 200 with no body, as the service does
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.OK).build());

        return ret;
    }

    /**
     * Runs the call with the caller in the thread-local the director reads. This body executes on a cache-read
     * thread, which is the whole point -- and those threads are pooled and reused, so the clear in the finally
     * is what keeps one caller's scope from leaking into the next request.
     *
     * <p><b>It is also where BOTH timing points are closed</b>, and they are closed here rather than further
     * down the chain for a reason that cost a build to find. A {@code doFinally} on the returned Mono looks
     * like the natural place for the gate window, but Reactor runs it AFTER the terminal signal has gone
     * downstream -- and the response write, and the filter's own {@code then()}, both happen inside that
     * propagation. The stamp would land after the request had already been reported. This method, by contrast,
     * runs entirely on the cache-read thread and returns before the value is emitted at all, so a value set
     * here is always in place. It also means the empty answer ({@code /esq-enode} finding nothing) and a
     * failed read are stamped too, because a finally does not care which way the call ended.
     *
     * <p>The gate window therefore ends when the ward is done rather than when the event loop picks the answer
     * back up. The hand-back it leaves out is a scheduler signal, not work.
     */
    private <T> T scoped(ServerWebExchange exchange, long outFromGate, EsqRequestContext ctx, Callable<T> call)
            throws Exception {
        T ret;
        // IN THE WARD -- the work begins. Nothing before this line ran on this thread.
        long inTheWard = System.currentTimeMillis();

        EsqContextHolder.set(ctx);
        try {
            ret = call.call();
        } finally {
            EsqContextHolder.clear();
            TreeRouteTimingFilter.wardOuter(exchange, inTheWard);
            TreeRouteTimingFilter.gateInner(exchange, outFromGate);
        }

        return ret;
    }
}
