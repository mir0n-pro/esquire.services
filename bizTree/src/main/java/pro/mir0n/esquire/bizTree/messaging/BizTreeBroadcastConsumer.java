/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: the entity-broadcast intake. The shared XRodManager opens the receive x-Rod on the
 *                   {esquire.entity, entity-update-broadcast} leg, handing each event to the director (-> the
 *                   cache monad). The manager owns the receive pool + transport consumer + start/stop, so this
 *                   class is just the one wiring line (was BizTreeBroadcastConfig).
 */
package pro.mir0n.esquire.bizTree.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

/** Opens the entity-broadcast receive x-Rod feeding the bizTree cache director. */
@Slf4j
@Component
@ConditionalOnProperty(name = "biztree.entity-broadcast-bus.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class BizTreeBroadcastConsumer {

    private final IBizTreeDirector director;

    public BizTreeBroadcastConsumer(IBizTreeDirector director, XRodManager rods) {
        this.director = director;
        rods.consumer(EsqMsgConstants.BUS_KEY_ENTITY, Role.BROADCAST, this::onRodEvent);
    }

    /** Receive one entity-broadcast event: app-level console log (uniform with the producers and the kc
     *  consumers), then hand it to the cache director. The separate TX/RX msg-audit rides the x-Rod msgLog. */
    public void onRodEvent(RodEvent e) {
        log.info("ENTITY | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId());
        director.onRodEvent(e);
    }
}
