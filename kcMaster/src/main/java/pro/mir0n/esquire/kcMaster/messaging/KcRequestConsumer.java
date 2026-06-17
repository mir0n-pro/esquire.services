/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URQ consumer; deserializes Text, dispatches to KcRequestHandler, publishes URS
 * 03/21/2026 mir0n  three-tier logging: kcAudit→msgLog/devLog; dual-mode URQ audit; MDC from message;
 *                   applMsgId read; dual error pattern with full context
 * 04/06/2026 mir0n  entityKind read from FIELD_ENTITY_KIND; forwarded to publishSuccess/publishFailure
 * 06/14/2026 mir0n  bus-oriented: rewired onto the x-Rod transport seam -- onRodEvent(RodEvent) instead of a
 *                   @JmsListener(Message). The body arrives already parsed (a Map -> KcSyncRequest via
 *                   convertValue); the requester's rod-id rides the event and is echoed back on the reply so
 *                   the originating producer's RodID selector matches. testReqId folded into the requestId.
 * 06/14/2026 mir0n  the request receive x-Rod is opened by the shared XRodManager (no separate config class);
 *                   no selector -- the request queue is shared work across kcMaster pods. The manager owns the
 *                   receive pool + transport consumer + start/stop.
 * 06/15/2026 mir0n  request consumer registers its onRodEvent worker via XRodManager.consumer(BUS_KEY_KC,
 *                   Role.SERVER); @ConditionalOnProperty kcmaster.kc-request-bus.consumer.enabled gates the bean.
 */

package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

@Slf4j
@Component
@ConditionalOnProperty(name = "kcmaster.kc-request-bus.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KcRequestConsumer {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcRequestConsumer.class.getName());

    private final KcRequestHandler handler;
    private final KcResponsePublisher publisher;
    private final ObjectMapper objectMapper;

    public KcRequestConsumer(KcRequestHandler handler, KcResponsePublisher publisher,
                             ObjectMapper objectMapper, XRodManager rods) {
        this.handler      = handler;
        this.publisher    = publisher;
        this.objectMapper = objectMapper;
        // no selector: the request queue is shared work -- any kcMaster pod takes the next URQ.
        rods.consumer(EsqMsgConstants.BUS_KEY_KC, Role.SERVER, this::onRodEvent);
    }

    /** Receive one URQ off the {esquire.kc, kc-request} leg (the request receive x-Rod's worker). */
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

            publisher.publishSuccess(entityId, entityKind, command, requesterRodId, requestId, correlationId);

        } catch (Exception ex) {
            log.error("kcMaster: URQ processing failed: entityId={}, command={}, rodId={}, error={}",
                    entityId, command, requesterRodId, ex.getMessage());
            devLog.error("kcMaster: URQ processing failed: entityId={}, command={}, rodId={}, requestId={}, correlationId={}, error={}",
                    entityId, command, requesterRodId, requestId, correlationId, ex.getMessage(), ex);
            publisher.publishFailure(entityId, entityKind, command, "KC_SYNC_ERROR", ex.getMessage(),
                    requesterRodId, requestId, correlationId, e.body());
        } finally {
            MDC.clear();
        }
    }

}
