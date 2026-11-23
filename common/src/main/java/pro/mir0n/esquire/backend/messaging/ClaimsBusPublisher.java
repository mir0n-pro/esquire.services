/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- the claims-bus publisher: evict(uid, correlationId,
 *                   requestId) sends a DELETE of the access profile; off, it sends nothing and takes no rod
 */

package pro.mir0n.esquire.backend.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.MessagingBus;
import pro.mir0n.messaging.RodEvent;

import java.util.Map;

//xxx: pure OAuth (lab) -- off, it sends nothing and takes no rod: the claims bus is not built in the claims mode
public class ClaimsBusPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimsBusPublisher.class);

    private final boolean enabled;
    private IXRod rod;

    public ClaimsBusPublisher(boolean enabled) {
        this.enabled = enabled;
    }

    public void start() {
        if (enabled) {
            rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_CLAIMS);
            rod.transmit(null);   // role-support: probe -- throws if the rod has no transmit leg
        }
        log.info("CLAIMS | publisher {}", enabled ? "on" : "off");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void evict(String uid, String correlationId, String requestId) {
        if (enabled && uid != null && !uid.isBlank()) {
            RodEvent e = new RodEvent(RodEvent.Op.DELETE, EsqConstants.KIND_ACCESS_PROFILE, uid, null, null,
                    System.currentTimeMillis(), correlationId, requestId, null, null,
                    BusConstants.MSG_TYPE_ENTITY_BROADCASTS, Map.of());
            rod.transmit(e);
            log.info("CLAIMS | evict | {} | {}", uid, correlationId);
        }
    }
}
