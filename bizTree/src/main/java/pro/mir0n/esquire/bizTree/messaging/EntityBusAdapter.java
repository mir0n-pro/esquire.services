/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was BizTreeBroadcastConsumer): the bizTree end of the entity bus (CLIENT) -- the
 *                   entity-broadcast receive worker (rod from the facade) feeding the cache director.
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.bizTree.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

/** The bizTree end of the entity bus (CLIENT role): the entity-broadcast receive worker feeding the cache director. */
@Slf4j
@Component
public class EntityBusAdapter {

    private final IBizTreeDirector director;

    public EntityBusAdapter(IBizTreeDirector director) {
        this.director = director;
        // entity CLIENT: receive the broadcast (no transmit leg).
        IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_ENTITY);
        rod.setWorker(this::onRodEvent);
    }

    /** Receive one entity-broadcast event: app-level console log (uniform with the producers and the kc
     *  consumers), then hand it to the cache director. The separate TX/RX msg-audit rides the x-Rod msgLog. */
    public void onRodEvent(RodEvent e) {
        log.info("ENTITY | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId());
        director.onRodEvent(e);
    }
}
