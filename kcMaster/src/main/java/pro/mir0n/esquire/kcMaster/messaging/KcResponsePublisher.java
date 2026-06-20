/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — publishes URS (success) and URR (request reject) to esquire.kc.response
 *                   whole message logged via LinkedHashMap props; URR carries RFC 9457 Error header
 * 03/21/2026 mir0n  three-tier logging: kcAudit→msgLog/devLog; mid extracted before props map;
 *                   dual-mode URS and URR audit; console echo log.info; dual error pattern
 * 03/26/2026 mir0n  MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING)
 * 04/06/2026 mir0n  publishSuccess/publishFailure: entityKind param added — echoes actual entity kind
 * 06/14/2026 mir0n  bus-oriented: the reply rides the x-Rod transport seam as a RodEvent on the
 *                   {esquire.kc, kc-response} catalog leg. One leg, two msg-types (URS / URR) chosen per
 *                   message via the publisherMsg sink. The requester's rod-id is echoed on the event so the
 *                   requester's RodID selector matches; a reject carries the RFC-9457 error under the body
 *                   "error" key (and the original request under "request"). testReqId rides as the requestId.
 * 06/15/2026 mir0n  reply producer obtained from XRodManager.producer(BUS_KEY_KC, Role.SERVER) as an IXRod;
 *                   publishSuccess/publishFailure build a RodEvent (MSG_TYPE_RESPONSE / MSG_TYPE_REJECT) and
 *                   rod.transmit() it; JmsTemplate/ObjectMapper/Session props wiring removed.
 */

package pro.mir0n.esquire.kcMaster.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the KC sync reply (URS success / URR reject) to the {esquire.kc, kc-response} leg. The requester's
 * rod-id is stamped on the event so only the originating producer instance's RodID selector picks the reply up.
 */
@Slf4j
@Component
public class KcResponsePublisher {

    private final IXRod rod;

    public KcResponsePublisher(XRodManager rods) {
        this.rod = rods.producer(EsqMsgConstants.BUS_KEY_KC, Role.SERVER);
    }

    public void publishSuccess(String entityId, int entityKind, String command,
                               String requesterRodId, String requestId, String correlationId) {
        RodEvent e = new RodEvent(RodEvent.opFromCode(command), entityKind, entityId, null,
                System.currentTimeMillis(), correlationId, requestId, null, requesterRodId,
                EsqMsgConstants.MSG_TYPE_RESPONSE, Map.of());
        rod.transmit(e);
        log.info("KC | URS | {} | {} | {} | {}", command, entityKind, entityId, requesterRodId);
    }

    public void publishFailure(String entityId, int entityKind, String command,
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
                EsqMsgConstants.MSG_TYPE_REJECT, body);
        rod.transmit(e);
        log.info("KC | URR | {} | {} | {} | {}", command, entityKind, entityId, requesterRodId);
    }

}
