/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: the kcMaster end of the kc bus (SERVER) -- one adapter, both legs. Merges the former
 *                   KcRequestConsumer (receive URQ) + KcResponsePublisher (transmit URS/URR) onto the single
 *                   kc-SERVER rod (from the facade). Receives a URQ off the request leg, dispatches to
 *                   KcRequestHandler, and replies URS (success) / URR (reject) on the response leg; the requester's
 *                   rod-id is echoed on the reply so only the originating instance's RodID selector picks it up.
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 */
package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The kcMaster end of the kc bus (SERVER role): one rod, both legs. It receives a URQ off the request leg (shared
 * work -- no selector, any kcMaster pod takes the next one), dispatches to {@link KcRequestHandler}, and transmits
 * the reply (URS success / URR reject) on the response leg. The requester's rod-id is stamped on the reply so only
 * the originating producer instance's RodID selector picks it up.
 */
@Slf4j
@Component
public class KcBusAdapter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcBusAdapter.class.getName());

    private final KcRequestHandler handler;
    private final ObjectMapper objectMapper;
    private final IXRod rod;

    public KcBusAdapter(KcRequestHandler handler, ObjectMapper objectMapper) {
        this.handler      = handler;
        this.objectMapper = objectMapper;
        // kc SERVER: receive URQ requests (no selector -- shared work) + transmit URS/URR replies, on one rod.
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_KC);
        this.rod.setWorker(this::onRodEvent);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);                // role-support: probe -- throws if the rod has no transmit leg
    }

    /** Receive one URQ off the {esquire.kc, kc-request} leg, handle it, and reply URS (success) or URR (reject). */
    public void onRodEvent(RodEvent e) {
        String command       = e.opCode();
        String entityId      = e.entityId();
        int    entityKind    = e.kind();
        String requesterRodId = e.rodId();
        String requestId     = e.requestId();
        String correlationId = e.correlationId();

        MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
        MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);
        try {
            log.info("KC | URQ | {} | {} | {} | {}", command, entityKind, entityId, requesterRodId);

            KcSyncRequest req = objectMapper.convertValue(e.body(), KcSyncRequest.class);
            handler.handle(command, req, correlationId, requestId);

            publishSuccess(entityId, entityKind, command, requesterRodId, requestId, correlationId);

        } catch (Exception ex) {
            log.error("kcMaster: URQ processing failed: entityId={}, command={}, rodId={}, error={}",
                    entityId, command, requesterRodId, ex.getMessage());
            devLog.error("kcMaster: URQ processing failed: entityId={}, command={}, rodId={}, requestId={}, correlationId={}, error={}",
                    entityId, command, requesterRodId, requestId, correlationId, ex.getMessage(), ex);
            publishFailure(entityId, entityKind, command, "KC_SYNC_ERROR", ex.getMessage(),
                    requesterRodId, requestId, correlationId, e.body());
        } finally {
            MDC.clear();
        }
    }

    /** Transmit a URS (success) reply on the response leg. */
    private void publishSuccess(String entityId, int entityKind, String command,
                                String requesterRodId, String requestId, String correlationId) {
        RodEvent e = new RodEvent(RodEvent.opFromCode(command), entityKind, entityId, null,
                System.currentTimeMillis(), correlationId, requestId, null, requesterRodId,
                BusConstants.MSG_TYPE_RESPONSE, Map.of());
        rod.transmit(e);
        log.info("KC | URS | {} | {} | {} | {}", command, entityKind, entityId, requesterRodId);
    }

    /** Transmit a URR (reject) reply on the response leg, carrying the RFC-9457 error + the original request. */
    private void publishFailure(String entityId, int entityKind, String command,
                                String errorCode, String errorMessage,
                                String requesterRodId, String requestId, String correlationId,
                                Map<String, Object> requestBody) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type",   "about:blank");
        error.put("title",  errorCode);
        error.put("status", 500);
        error.put("detail", errorMessage);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        if (requestBody != null) {
            body.put("request", requestBody);
        }
        RodEvent e = new RodEvent(RodEvent.opFromCode(command), entityKind, entityId, null,
                System.currentTimeMillis(), correlationId, requestId, null, requesterRodId,
                BusConstants.MSG_TYPE_REJECT, body);
        rod.transmit(e);
        log.info("KC | URR | {} | {} | {} | {}", command, entityKind, entityId, requesterRodId);
    }
}
