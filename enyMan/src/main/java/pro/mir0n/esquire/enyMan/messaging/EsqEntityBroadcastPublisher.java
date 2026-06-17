/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: publishes FIX-JSON envelope to esquire.entity.broadcast on entity update
 * 06/14/2026 mir0n  rewired onto the x-Rod transport seam; the leg is named {esquire.entity,
 *                   entity-update-broadcast} in the messaging-bus catalog (msg-type UE).
 * 06/14/2026 mir0n  the x-Rod is opened by the shared XRodManager; the send goes through rod.transmit; the
 *                   manager owns start/stop. No per-class lifecycle. publish() is unchanged (post-commit).
 * 06/15/2026 mir0n  net: the JMS producer (jmsTopicTemplate + FIX-JSON props) is gone; publish() builds a
 *                   RodEvent (opFromCode, msg-type UE) and calls rod.transmit on the IXRod from
 *                   XRodManager.producer(BUS_KEY_ENTITY, BROADCAST); service-id/ctrl-id/ObjectMapper/Utils dropped.
 */
package pro.mir0n.esquire.enyMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.Map;

/**
 * Publishes entity state events to the entity-broadcast TOPIC over the x-Rod transmit leg. The leg is named
 * {@code {esquire.entity, entity}} in the messaging-bus catalog; each call builds a
 * {@link RodEvent} (the event-type code -> Op, the snapshot -> body, msg-type UE) and sends it out the leg.
 * Publish only after the transaction commits (post-commit contract).
 */
@Slf4j
@Component
public class EsqEntityBroadcastPublisher {

    private final IXRod rod;

    public EsqEntityBroadcastPublisher(XRodManager rods) {
        this.rod = rods.producer(EsqMsgConstants.BUS_KEY_ENTITY, Role.BROADCAST);
    }

    /**
     * Publish an entity state event.
     *
     * @param entityKind    FIX-JSON EntityKind value
     * @param entityId      FIX-JSON EntityID value
     * @param eventType     C / U / D / X (EsqMsgConstants.EVENT_*)
     * @param requestId     from request context; may be null
     * @param correlationId from correlation context; may be null
     * @param text          entity state snapshot (may be null)
     */
    public void publish(int entityKind, String entityId, String eventType,
                        String requestId, String correlationId, Map<String, Object> text) {
        RodEvent e = new RodEvent(RodEvent.opFromCode(eventType), entityKind, entityId, null,
                System.currentTimeMillis(), correlationId, requestId, null, null,
                EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS, text != null ? text : Map.of());
        rod.transmit(e);
        log.info("ENTITY | UE | {} | {} | {} | {}", eventType, entityKind, entityId, correlationId);
    }
}
