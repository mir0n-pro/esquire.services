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
 *                   (UPDATE_PATH) it parks the new path in KcPathBuffer when the KC user does not exist yet; the
 *                   URQ handler owns the update when it does.
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
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
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;

import java.util.List;
import java.util.Map;

/**
 * Race-8c safety-net receive worker (the kcMaster end of the entity bus, CLIENT role).
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
public class EntityBusAdapter {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + EntityBusAdapter.class.getName());

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;
    private final KcPathBuffer pathBuffer;

    public EntityBusAdapter(Keycloak keycloak, KeycloakConfig keycloakConfig, KcPathBuffer pathBuffer) {
        this.keycloak = keycloak;
        this.keycloakConfig = keycloakConfig;
        this.pathBuffer = pathBuffer;
        // entity CLIENT: receive the broadcast (no transmit leg).
        IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_ENTITY);
        rod.setWorker(this::onRodEvent);
    }

    /** Receive one entity-broadcast event off the bus. */
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
            Object pathValue = body.get(EsqConstants.TEXT_PATH);
            if (pathValue != null) {
                ret = pathValue.toString();
            }
        }
        return ret;
    }
}
