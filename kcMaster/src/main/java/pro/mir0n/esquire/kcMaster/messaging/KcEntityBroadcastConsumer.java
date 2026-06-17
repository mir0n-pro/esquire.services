/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: race-8c safety-net subscriber on esquire.entity.broadcast topic.
 *                   Filters EVENT_UPDATE_PATH ("X"); if the KC user exists, no-op (the URQ
 *                   handler is the imperative channel); if the KC user does not exist yet,
 *                   parks the path in KcPathBuffer so KcIdentityService.createUser can flush it.
 * 06/14/2026 mir0n  rewired onto the x-Rod transport seam: this is now the receive worker fed by the
 *                   broadcast receive x-Rod (KcMasterBroadcastConfig) -- onRodEvent(RodEvent) instead of a
 *                   @JmsListener(Message). The body arrives already parsed (a Map), so the path is read off
 *                   it directly (no readTree). Move detection is RodEvent.Op.UPDATE_PATH ("X").
 * 06/15/2026 mir0n  broadcast worker registers via XRodManager.consumer(BUS_KEY_ENTITY, Role.BROADCAST) in
 *                   the constructor (no separate config class); extractPath reads the path off the body Map
 *                   (no static ObjectMapper); JMS selector/imports removed.
 */
package pro.mir0n.esquire.kcMaster.messaging;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;

import java.util.List;
import java.util.Map;

/**
 * Race-8c safety-net receive worker.
 *
 * <p>The URQ EVENT_UPDATE_PATH handler is the authoritative imperative channel: it updates KC when the user
 * is there and silent-skips when the user is not -- but the silent-skip drops the path on the floor. enyMan's
 * move also publishes the same move ({@link RodEvent.Op#UPDATE_PATH}) on the entity-broadcast TOPIC; this
 * worker picks it up and, when the KC user is missing, parks the new path in {@link KcPathBuffer}. The next
 * keySmith CREATE URQ for that entity flushes the buffer in {@code KcIdentityService.createUser}.
 *
 * <p>Multi-instance safety: the TOPIC broadcasts to every kcMaster pod, each holds its own buffer, the pod
 * that handles the CREATE URQ flushes its own buffer -- no shared state. Never modifies KC when the user
 * exists (the URQ handler owns that).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kcmaster.entity-broadcast-bus.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KcEntityBroadcastConsumer {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcEntityBroadcastConsumer.class.getName());

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;
    private final KcPathBuffer pathBuffer;

    public KcEntityBroadcastConsumer(Keycloak keycloak, KeycloakConfig keycloakConfig, KcPathBuffer pathBuffer,
                                     XRodManager rods) {
        this.keycloak = keycloak;
        this.keycloakConfig = keycloakConfig;
        this.pathBuffer = pathBuffer;
        rods.consumer(EsqMsgConstants.BUS_KEY_ENTITY, Role.BROADCAST, this::onRodEvent);
    }

    /** Receive one entity-broadcast event off the bus (the broadcast receive x-Rod's worker). */
    public void onRodEvent(RodEvent e) {
        MDC.put(EsqConstants.PD_REQUEST_ID,     e.requestId());
        MDC.put(EsqConstants.PD_CORRELATION_ID, e.correlationId());
        try {
            // Only a move (UPDATE_PATH / "X") drives the race-8c buffer; everything else is someone else's.
            if (e.op() != RodEvent.Op.UPDATE_PATH) {
                return;
            }

            String newPath = extractPath(e.body());
            if (newPath == null) {
                devLog.debug("KC | TOPIC-X | entityId={} : no path in body, skipping", e.entityId());
                return;
            }

            if (kcUserExists(e.entityId())) {
                // URQ handler owns the update for existing users. Topic-side stays passive.
                devLog.debug("KC | TOPIC-X | entityId={} : KC user exists, URQ owns update", e.entityId());
                return;
            }

            pathBuffer.store(e.entityId(), newPath);
            log.info("KC | TOPIC-X | entityId={} | path={} | BUFFERED (no KC user yet)", e.entityId(), newPath);
        } finally {
            MDC.clear();
        }
    }

    private boolean kcUserExists(String entityId) {
        boolean ret = false;
        if (entityId != null) {
            RealmResource realm = keycloak.realm(keycloakConfig.getRealm());
            UsersResource users = realm.users();
            List<UserRepresentation> found = users.searchByAttributes(
                    EsqConstants.JWT_CLAIM_ENTITY_ID + ":" + entityId, true);
            ret = found != null && !found.isEmpty();
        }
        return ret;
    }

    private String extractPath(Map<String, Object> body) {
        String ret = null;
        if (body != null) {
            Object pathValue = body.get(EsqMsgConstants.TEXT_PATH);
            if (pathValue != null) {
                ret = pathValue.toString();
            }
        }
        return ret;
    }
}
