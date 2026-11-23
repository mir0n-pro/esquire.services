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
 */

package pro.mir0n.esquire.gateway.lab.pureoauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.MessagingBus;
import pro.mir0n.messaging.RodEvent;

public class PureOAuthEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(PureOAuthEvictionListener.class);

    private final PureOAuthClaimsCache cache;
    private final boolean enabled;

    public PureOAuthEvictionListener(PureOAuthClaimsCache cache, boolean enabled) {
        this.cache = cache;
        this.enabled = enabled;
    }

    public void start() {
        if (enabled) {
            IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_CLAIMS);
            rod.setWorker(this::onEvent);   // role-support: throws if the rod has no receive leg
        }
        log.info("PURE OAUTH | eviction listener {}", enabled ? "on" : "off");
    }

    void onEvent(RodEvent e) {
        if (e != null && e.op() == RodEvent.Op.DELETE && e.kind() == EsqConstants.KIND_ACCESS_PROFILE
                && e.entityId() != null) {
            cache.evict(e.entityId());
            log.info("PURE OAUTH | evict | {} | {}", e.entityId(), e.correlationId());
        }
    }
}
