/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was KcEntityBroadcastConsumer): the kcMaster end of the entity bus (CLIENT) -- the
 *                   race-8c safety-net receive worker on the entity-broadcast rod (from the facade). On a move
 *                   (UPDATE_PATH) it parks the new path in the race-8c ExpiringCache when the KC user does not exist yet; the
 *                   URQ handler owns the update when it does.
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/15/2026 mir0n  v1.2.11 T11 -- the entity-broadcast receive worker stamps MDC via
 *                   EsqContextHolder.applyMessage(event) and clears in a finally (I10)
 * 08/11/2026 mir0n  v1.2.12 -- the path park moved to the shared ExpiringCache and now holds a ParkedPath:
 *                   storeIfGreater keeps the newest path by its path change number instead of the last
 *                   arrival, in one atomic step
 * 08/12/2026 mir0n  v1.2.13 -- transport only: the receive worker is KcIdentityGateway.serve; the park decision, the KC
 *                   user lookup and the path extraction moved to the gateway
 */
package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.kcMaster.identity.KcIdentityGateway;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;

/**
 * The kcMaster end of the entity bus (CLIENT role), receive only: every broadcast goes straight to the identity
 * gateway, which decides what a move that KeyCloak cannot apply yet is worth holding.
 *
 * <p>No identity work happens here either. What the safety net IS -- park the new path when the KeyCloak user
 * does not exist, stay passive when it does -- lives with the rest of the workflow in {@link KcIdentityGateway},
 * so the request side and the broadcast side cannot drift apart.
 *
 * <p>Multi-instance safety comes from the TOPIC: it reaches every kcMaster pod, each holding its own park, and
 * the pod that ends up handling the CREATE flushes its own. That is the whole reason the safety net is a
 * broadcast and not a second request.
 */
@Slf4j
@Component
public class EntityBusAdapter {

    private final KcIdentityGateway gateway;

    public EntityBusAdapter(KcIdentityGateway gateway) {
        this.gateway = gateway;
    }

    /** Takes the entity leg and points it at the gateway. Receive only -- there is no transmit side here. */
    @PostConstruct
    public void start() {
        IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_ENTITY);
        rod.setWorker(gateway::serve);
    }
}
