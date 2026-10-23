/*
 *  Esquire frameworks (tm)
 *  gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: the CLIENT end of the biz bus: send() posts a command and returns a Mono that
 *                   completes with the answer of the same correlation id; 503 when the leg is DOWN or max-pending
 *                   commands wait, 409 for a correlation id already waiting, a timeout past timeout-seconds (a
 *                   sweep every sweep-ms)
 */

package pro.mir0n.esquire.gateway.bus;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.MessagingBus;
import pro.mir0n.messaging.RodEvent;
import pro.mir0n.messaging.transport.TransportHealth;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The gateway end of the biz bus (CLIENT role): posts a command and hands back a Mono that completes with the
 * answer. No thread waits -- the command's correlation id sits in a map until the answer with that
 * correlation id arrives or its deadline passes.
 */
@Slf4j
@Component
public class BizBusClient {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizBusClient.class.getName());

    private record Pending(Sinks.One<RodEvent> sink, long deadline) {}

    private final long timeoutMs;
    private final int maxPending;
    private final long sweepMs;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "biz-bus-sweep"));
    // The tracing facade -- present only when tracing is enabled.
    private final ObjectProvider<Tracer> tracerProvider;
    private IXRod rod;

    public BizBusClient(@Value("${esq.gateway.biz-bus.timeout-seconds:30}") long timeoutSeconds,
                        @Value("${esq.gateway.biz-bus.max-pending:10000}") int maxPending,
                        @Value("${esq.gateway.biz-bus.sweep-ms:500}") long sweepMs,
                        ObjectProvider<Tracer> tracerProvider) {
        this.timeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        this.maxPending = maxPending;
        this.sweepMs = sweepMs;
        this.tracerProvider = tracerProvider;
    }

    @PostConstruct
    public void start() {
        attach(MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_BIZ));
        sweeper.scheduleWithFixedDelay(this::sweep, sweepMs, sweepMs, TimeUnit.MILLISECONDS);
    }

    void attach(IXRod biz) {
        this.rod = biz;
        this.rod.setWorker(this::onAnswer);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);              // role-support: probe -- throws if the rod has no transmit leg
    }

    @PreDestroy
    public void stop() {
        sweeper.shutdownNow();
    }

    /** Posts the command, keyed by its correlation id; the Mono completes with the answer, or fails with 503
     *  (leg down, map full), 409 (that correlation id is already waiting) or a timeout (no answer by the
     *  deadline). {@code span} is the request's span, or null when tracing is off. */
    public Mono<RodEvent> send(RodEvent command, Span span) {
        Mono<RodEvent> ret;
        String key = command.correlationId();
        if (rod.health() == TransportHealth.DOWN) {
            ret = Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "biz bus is down"));
        } else if (pending.size() >= maxPending) {
            ret = Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "biz bus: too many commands waiting for an answer"));
        } else {
            Pending entry = new Pending(Sinks.one(), System.currentTimeMillis() + timeoutMs);
            //xxx: into the map BEFORE the send -- a fast answer must find its correlation id
            if (pending.putIfAbsent(key, entry) != null) {
                ret = Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "biz bus: a request with this correlation id is already waiting: " + key));
            } else {
                try {
                    transmit(command, span);
                    devLog.debug("BIZ | URQ | {} | {}", command.body().get(EsqConstants.BIZ_KEY_OPERATION), key);
                    //xxx: doFinally also covers a cancel (the route's TimeLimiter, a client gone); publishOn lets
                    // the bus receive thread go before the response is written
                    ret = entry.sink().asMono()
                            .publishOn(Schedulers.parallel())
                            .doFinally(s -> pending.remove(key, entry));
                } catch (Exception e) {
                    pending.remove(key, entry);
                    ret = Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "biz bus: the command was not sent", e));
                }
            }
        }
        return ret;
    }

    //xxx: the request's span is current only here, around the send: Herald stamps the traceparent from the CURRENT
    // span (EsqRodObserver.outbound), and in the reactive chain the request's span is not on this thread
    private void transmit(RodEvent command, Span span) {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (span != null && tracer != null) {
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                rod.transmit(command);
            }
        } else {
            rod.transmit(command);
        }
    }

    void onAnswer(RodEvent answer) {
        Pending p = answer.correlationId() != null ? pending.remove(answer.correlationId()) : null;
        if (p != null) {
            p.sink().tryEmitValue(answer);
        } else {
            EsqContextHolder.applyMessage(answer);
            try {
                log.warn("BIZ | {} | no request waits for this answer (deadline passed or client gone), dropped: "
                        + "correlationId={}", answer.msgType(),
                        answer.correlationId());
            } finally {
                EsqContextHolder.clear();
            }
        }
    }

    void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Pending> en : pending.entrySet()) {
            Pending p = en.getValue();
            if (p.deadline() < now && pending.remove(en.getKey(), p)) {
                p.sink().tryEmitError(new TimeoutException("biz bus: no answer within " + timeoutMs + "ms"));
            }
        }
    }

    int pendingCount() {
        return pending.size();
    }
}
