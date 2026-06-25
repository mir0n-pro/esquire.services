/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was EsqEntityBroadcastPublisher): the pacMan end of the entity bus (SERVER) -- the
 *                   transmit leg onto the entity-broadcast TOPIC (rod from the facade). publish() builds a
 *                   RodEvent (msg-type UE) and transmits it post-commit.
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 */
package pro.mir0n.esquire.pacMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.Map;

/**
 * The pacMan end of the entity bus (SERVER role): the transmit leg onto the entity-broadcast TOPIC. Each call
 * builds a {@link RodEvent} (msg-type UE) and sends it out the leg. Publish only after the transaction commits.
 */
@Slf4j
@Component
public class EntityBusAdapter {

    private final IXRod rod;

    public EntityBusAdapter() {
        // entity SERVER: transmit only (no receive worker).
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_ENTITY);
        this.rod.transmit(null);   // role-support: probe -- throws if the rod has no transmit leg
    }

    /**
     * Publish an entity state event.
     *
     * @param entityKind    FIX-JSON EntityKind value
     * @param entityId      FIX-JSON EntityID value
     * @param eventType     C / U / D / X (BusConstants.EVENT_*)
     * @param requestId     from request context; may be null
     * @param correlationId from correlation context; may be null
     * @param text          entity state snapshot (may be null)
     */
    public void publish(int entityKind, String entityId, String eventType,
                        String requestId, String correlationId, Map<String, Object> text) {
        RodEvent e = new RodEvent(RodEvent.opFromCode(eventType), entityKind, entityId, null,
                System.currentTimeMillis(), correlationId, requestId, null, null,
                BusConstants.MSG_TYPE_ENTITY_BROADCASTS, text != null ? text : Map.of());
        rod.transmit(e);
        log.info("ENTITY | UE | {} | {} | {} | {}", eventType, entityKind, entityId, correlationId);
    }
}
