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
 * 07/15/2026 mir0n  v1.2.11 T11 -- the kc-bus receive worker stamps MDC via EsqContextHolder.applyMessage(event)
 *                   and clears in a finally (I10)
 * 08/11/2026 mir0n  v1.2.12 -- the RodEvent constructor call carries a null change number: a KeyCloak
 *                   request leg reports none
 * 08/12/2026 mir0n  v1.2.13 -- transport only: the receive worker is KcIdentityGateway.serve and the gateway's answers
 *                   transmit back on the rod; the dispatch and the URS/URR building moved to the gateway
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
 * The kcMaster end of the kc bus (SERVER role): one rod, both legs, and nothing else. Each received URQ goes to
 * the identity gateway, and whatever the gateway answers is transmitted back -- URS on success, URR on a reject.
 *
 * <p>No identity work happens here. The workflow has one home, {@link KcIdentityGateway}, and this class only
 * says that the work arrives as a message and that the answer leaves the same way. The gateway serves on THIS
 * thread, so the rod's worker pool still bounds how many syncs run at once.
 *
 * <p>The request leg carries no selector -- shared work, any kcMaster pod takes the next one. The gateway
 * stamps the requester's rod-id on the answer, so only the originating producer instance's RodID selector picks
 * it up.
 */
@Slf4j
@Component
public class KcBusAdapter {

    private final KcIdentityGateway gateway;
    private IXRod rod;

    public KcBusAdapter(KcIdentityGateway gateway) {
        this.gateway = gateway;
    }

    /** Takes the kc leg and points it at the gateway, with the answers going back out the same rod. */
    @PostConstruct
    public void start() {
        // kc SERVER: receive URQ requests (no selector -- shared work) + transmit URS/URR replies, on one rod.
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_KC);
        this.rod.setWorker(gateway::serve);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);              // role-support: probe -- throws if the rod has no transmit leg
        gateway.setResultHandler(this.rod::transmit);
    }
}
