/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/06/2026 mir0n  created: publishes EVENT_UPDATE_PATH URQ to kcMaster on entity move
 * 06/14/2026 mir0n  bus-oriented: the URQ rides the x-Rod transport seam as a RodEvent (op=UPDATE_PATH,
 *                   body={id,kind,path}) to the {esquire.kc, kc-request} catalog leg, msg-type URQ.
 * 06/14/2026 mir0n  the x-Rod is opened by the shared XRodManager (one frontend, common to every service);
 *                   the send goes through rod.transmit; the manager owns start/stop. No per-class lifecycle.
 * 06/15/2026 mir0n  net: the JMS producer (jmsQueueTemplate + FIX-JSON props) is gone; publishPathUpdate()
 *                   builds a RodEvent (op=UPDATE_PATH, msg-type URQ) and calls rod.transmit on the IXRod from
 *                   XRodManager.producer(BUS_KEY_KC, CLIENT); ctrl-id/ObjectMapper/Utils dropped.
 */
package pro.mir0n.esquire.enyMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes an EVENT_UPDATE_PATH URQ to kcMaster after a USR entity move (fire-and-forget; the tx already
 * committed). The URQ is a {@link RodEvent} on the {esquire.kc, kc-request} leg; kcMaster updates esq_rootpath
 * in KeyCloak and replies on the response leg, routed back by this enyMan instance's rod-id.
 */
@Slf4j
@Component
public class KcRequestPublisher {

    private final IXRod rod;

    public KcRequestPublisher(XRodManager rods) {
        this.rod = rods.producer(EsqMsgConstants.BUS_KEY_KC, Role.CLIENT);
    }

    public void publishPathUpdate(String entityId, int entityKind, String newPath,
                                  String requestId, String correlationId) {
        // guarantee a non-null tracking id (the former testReqId; it rides as the requestId on the wire).
        String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id",   entityId);
            body.put("kind", entityKind);
            body.put("path", newPath);
            RodEvent e = new RodEvent(RodEvent.Op.UPDATE_PATH, entityKind, entityId, null,
                    System.currentTimeMillis(), correlationId, reqId, null, null, EsqMsgConstants.MSG_TYPE_REQUEST, body);
            rod.transmit(e);
            log.info("KC | URQ | {} | {} | {} | {}", EsqMsgConstants.EVENT_UPDATE_PATH, entityKind, entityId, reqId);
        } catch (Exception ex) {
            // fire-and-forget: the move tx already committed, so a publish failure is logged for reconciliation.
            log.error("enyMan: failed to publish URQ: entityId={}, requestId={}, error={}", entityId, reqId, ex.getMessage());
        }
    }
}
