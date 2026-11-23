/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- the claims-bus worker: a DELETE of an access profile
 *                   evicts its uid from PureOAuthClaimsCache; off, it takes no rod
 * 11/22/2026 mir0n  v2.0.3 -- lab.pureoauth.PureOAuthEvictionListener -> oauth.EvictionListener; always takes
 *                   the claims rod (the on / off flag out); log prefix OAUTH
 */

package pro.mir0n.esquire.gateway.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.MessagingBus;
import pro.mir0n.messaging.RodEvent;

public class EvictionListener {

    private static final Logger log = LoggerFactory.getLogger(EvictionListener.class);

    private final ClaimsCache cache;

    public EvictionListener(ClaimsCache cache) {
        this.cache = cache;
    }

    public void start() {
        IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_CLAIMS);
        rod.setWorker(this::onEvent);   // role-support: throws if the rod has no receive leg
        log.info("OAUTH | eviction listener on");
    }

    void onEvent(RodEvent e) {
        if (e != null && e.op() == RodEvent.Op.DELETE && e.kind() == EsqConstants.KIND_ACCESS_PROFILE
                && e.entityId() != null) {
            cache.evict(e.entityId());
            log.info("OAUTH | evict | {} | {}", e.entityId(), e.correlationId());
        }
    }
}
