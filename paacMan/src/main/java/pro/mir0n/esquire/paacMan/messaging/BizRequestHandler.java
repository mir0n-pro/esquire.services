/*
 *  Esquire frameworks (tm)
 *  paacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: serves one biz request: the caller's claims from the message into the request context
 *                   (EsqRequestScope), the JPA timing, the INCOMING / OUTGOING lines and the esq.srv.* meters, then
 *                   the PacManCommands operation the request names (esq-acct, esq-cmd, esq-cmd-save, esq-cmd-del);
 *                   the answer is URS on a 2xx, URR with the problem detail otherwise
 */

package pro.mir0n.esquire.paacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.error.GenericExceptionHandler;
import pro.mir0n.esquire.backend.error.GenericRuntimeException;
import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.service.PerformanceHolder;
import pro.mir0n.esquire.backend.service.RequestPerformance;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.service.PacManCommands;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import pro.mir0n.esquire.backend.service.EsqRequestScope;
import pro.mir0n.esquire.backend.messaging.IBusServer;

/**
 * Serves one biz request from the bus, the way the servlet chain serves a REST call: the caller's claims from
 * the message, the per-request context and MDC, the JPA timing, the INCOMING / OUTGOING lines and the
 * esq.srv.outer / esq.srv.inner meters -- then pacMan's commands, the same ones its REST face calls, then one
 * answer back: URS on a 2xx, URR otherwise. The request work is common's EsqRequestScope, as for REST. It runs on the
 * rod's receive pool, so that pool bounds how many requests run at once.
 */
@Slf4j
@Component
public class BizRequestHandler implements IBusServer {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizRequestHandler.class.getName());

    private final PacManCommands commands;
    private final ObjectMapper objectMapper;
    private final EsqRequestScope scope;
    private volatile Consumer<RodEvent> resultHandler;

    public BizRequestHandler(PacManCommands commands, ObjectMapper objectMapper, EsqRequestScope scope) {
        this.commands = commands;
        this.objectMapper = objectMapper;
        this.scope = scope;
    }

    @Override
    public void setResultHandler(Consumer<RodEvent> handler) {
        this.resultHandler = handler;
    }

    @Override
    public void serve(RodEvent request) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> body = request.body() != null ? request.body() : Map.of();
        String operation = String.valueOf(body.get(EsqConstants.BIZ_KEY_OPERATION));
        RequestPerformance performance = new RequestPerformance();
        performance.setMetricsCaptured(Boolean.TRUE.equals(body.get(EsqConstants.BIZ_KEY_CAPTURE)));
        PerformanceHolder.set(performance);
        EsqContextHolder.applyMessage(request);

        HttpStatus status = HttpStatus.OK;
        Object result = null;
        ProblemDetail problem = null;
        try {
            // Log arrival AFTER the MDC ids are set, so the INCOMING line carries them like every other line.
            scope.incoming(log, "BUS " + operation);
            String uid = request.uid();
            Object rootPath = body.get(EsqConstants.BIZ_KEY_ROOT_PATH);
            List<String> roles = rolesOf(body);
            if (!EsqRequestScope.claimsComplete(uid, rootPath, roles)) {
                devLog.debug("Missing required claims: {}={}, {}={}, {}={}", EsqConstants.PD_UID, uid != null,
                        EsqConstants.BIZ_KEY_ROOT_PATH, rootPath != null,
                        EsqConstants.BIZ_KEY_ROLES, roles != null && !roles.isEmpty());
                status = HttpStatus.UNAUTHORIZED;
                problem = ProblemDetailMill.createProblemDetail(operation, request.correlationId(),
                        request.requestId(), performance.isMetricsCaptured(), status, "Authentication Failed",
                        "Missing required claims", null);
            } else {
                EsqRequestScope.bind(new EsqRequestContext(request.correlationId(), request.requestId(), uid,
                        rootPath.toString(), roles));
                result = dispatch(operation, request, body);
            }
        } catch (Exception ex) {
            status = GenericExceptionHandler.statusOf(ex);
            if (ex instanceof GenericRuntimeException) {
                log.error(ex.getClass().getSimpleName() + ": BUS {}: {}", operation, ex.getMessage());
                devLog.error(ex.getClass().getSimpleName() + ": BUS {}: {}", operation, ex.getMessage(), ex);
            } else {
                log.error("Unhandled exception: BUS {}", operation);
                devLog.error("Unhandled exception: BUS {}", operation, ex);
            }
            problem = ProblemDetailMill.createProblemDetail(operation, request.correlationId(), request.requestId(),
                    performance.isMetricsCaptured(), status, GenericExceptionHandler.titleOf(ex), null, ex);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            answer(request, status, result, problem, performance, duration);
            scope.outgoing(log, "BUS " + operation, operation, status.value(), duration, performance);

            // a pooled receive thread must never carry one caller's identity or timing into the next request
            EsqRequestScope.unbind();
            PerformanceHolder.clear();
        }
    }

    private Object dispatch(String operation, RodEvent request, Map<String, Object> body) {
        Object ret = null;
        Integer kind = request.kind();
        String id = request.entityId();
        Object cmdValue = body.get(EsqConstants.BIZ_KEY_CMD);
        String cmd = cmdValue != null ? cmdValue.toString() : null;
        switch (operation) {
        case EsqConstants.BIZ_OP_ACCT:
            ret = commands.esquireCommandAcct(kind, id, cmd != null ? cmd : "acct", fieldsOf(body));
            break;
        case EsqConstants.BIZ_OP_CMD:
            ret = commands.esquireCommand(kind, id, cmd != null ? cmd : "details");
            break;
        case EsqConstants.BIZ_OP_CMD_SAVE:
            ret = commands.esquireCommandSave(kind, id, cmd != null ? cmd : "save", fieldsOf(body));
            break;
        case EsqConstants.BIZ_OP_CMD_DEL:
            commands.esquireCommandDelete(kind, id, cmd != null ? cmd : "delete");
            break;
        default:
            throw new GenericRuntimeException("Unknown operation: " + operation);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Map<String, Object> body) {
        Object roles = body.get(EsqConstants.BIZ_KEY_ROLES);
        return roles instanceof List ? (List<String>) roles : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldsOf(Map<String, Object> body) {
        Object fields = body.get(EsqConstants.BIZ_KEY_FIELDS);
        return fields instanceof Map ? (Map<String, Object>) fields : Map.of();
    }

    private void answer(RodEvent request, HttpStatus status, Object result, ProblemDetail problem,
                        RequestPerformance performance, long duration) {
        Consumer<RodEvent> sink = this.resultHandler;
        if (sink != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(EsqConstants.BIZ_KEY_STATUS, status.value());
            if (problem != null) {
                body.put(EsqConstants.BIZ_KEY_PROBLEM, objectMapper.convertValue(problem, Map.class));
            } else if (result != null) {
                body.put(EsqConstants.BIZ_KEY_RESULT, objectMapper.convertValue(result, Object.class));
            }
            if (performance.isMetricsCaptured()) {
                body.put(EsqConstants.BIZ_KEY_OUTER_MS, duration);
                body.put(EsqConstants.BIZ_KEY_INNER_MS, performance.getTotalJpaTime());
            }
            String msgType = status.is2xxSuccessful() ? BusConstants.MSG_TYPE_RESPONSE : BusConstants.MSG_TYPE_REJECT;
            sink.accept(new RodEvent(request.op(), request.kind(), request.entityId(), null, null,
                    System.currentTimeMillis(), request.correlationId(), request.requestId(), null,
                    request.rodId(), msgType, body));
        }
    }
}
