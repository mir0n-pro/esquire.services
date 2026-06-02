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
 *                   handler in KcRequestHandler.handleUpdatePath is the imperative channel
 *                   and has already / will / silent-skip); if the KC user does not exist yet,
 *                   parks the path in KcPathBuffer so KcIdentityService.createUser can flush
 *                   it onto the freshly-minted user once keySmith's CREATE URQ arrives.
 */
package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;

import java.util.List;

/**
 * Race-8c safety-net consumer.
 *
 * <p>The URQ EVENT_UPDATE_PATH handler ({@link KcRequestHandler}) is the
 * authoritative imperative channel: it updates KC when the user is there and
 * silent-skips when the user is not -- but the silent-skip drops the path on
 * the floor. enyMan's move also publishes the same EVENT_UPDATE_PATH on the
 * entity broadcast TOPIC; this consumer picks it up off the topic and, when
 * the KC user is missing, parks the new path in {@link KcPathBuffer}. The next
 * keySmith EVENT_CREATE URQ for that entity will flush the buffer in {@code
 * KcIdentityService.createUser} so the post-move path lands on the user.
 *
 * <p>Subscription is NON-DURABLE (matches v1.2.5 bizTree shape): the buffer
 * is in-memory and ephemeral by design; durability would only persist already-
 * stale paths past kcMaster restarts. Multi-instance safety: TOPIC broadcasts
 * to every kcMaster pod, each holds its own buffer, the pod that handles the
 * CREATE URQ flushes its own buffer -- no shared state.
 *
 * <p>This listener never modifies KC when the user exists. The URQ handler
 * owns that semantic; doing it here too would cause redundant attribute
 * writes against KC for every move-cascade.
 */
@Slf4j
@Component
public class KcEntityBroadcastConsumer {

    private static final Logger msgLog = LoggerFactory.getLogger("msg."     + KcEntityBroadcastConsumer.class.getName());
    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcEntityBroadcastConsumer.class.getName());

    private static final String MSG_SELECTOR =
            EsqMsgConstants.FIELD_BUS_ID   + " = '" + EsqMsgConstants.BUS_ID_ENTITY              + "'" +
            " AND " +
            EsqMsgConstants.FIELD_MSG_TYPE + " = '" + EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS + "'";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;
    private final KcPathBuffer pathBuffer;

    public KcEntityBroadcastConsumer(Keycloak keycloak, KeycloakConfig keycloakConfig, KcPathBuffer pathBuffer) {
        this.keycloak = keycloak;
        this.keycloakConfig = keycloakConfig;
        this.pathBuffer = pathBuffer;
    }

    @JmsListener(
        destination      = EsqMsgConstants.TOPIC_ENTITY_BROADCAST,
        containerFactory = "jmsTopicListenerFactory",
        selector         = MSG_SELECTOR
    )
    public void onEntityBroadcast(Message message) {
        String applMsgId = null;
        try {
            applMsgId            = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String eventType     = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind    = message.getIntProperty(   EsqMsgConstants.FIELD_ENTITY_KIND);
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String textJson      = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            MDC.put(EsqConstants.PD_REQUEST_ID,     requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            // Msg-audit receipt: record EVERY message that clears the broker selector,
            // BEFORE any application-level filtering. This keeps "message never arrived"
            // (no line) distinguishable from "arrived but not EVENT_UPDATE_PATH" (line
            // present, eventType != X) when diagnosing the race-8c topic path.
            msgLog.info("ENTITY | UE | {} | {} | {} | {}",
                    applMsgId, eventType, entityKind, entityId);

            // Only EVENT_UPDATE_PATH ("X") drives the race-8c buffer; everything else
            // is somebody else's business (bizTree's cache, etc.).
            if (!EsqMsgConstants.EVENT_UPDATE_PATH.equals(eventType)) {
                return;
            }

            String newPath = extractPath(textJson);
            if (newPath == null) {
                devLog.debug("KC | TOPIC-X | entityId={} : no path in textJson, skipping", entityId);
                return;
            }

            if (kcUserExists(entityId)) {
                // URQ handler owns the update for existing users. Topic-side stays passive.
                devLog.debug("KC | TOPIC-X | entityId={} : KC user exists, URQ owns update", entityId);
                return;
            }

            pathBuffer.store(entityId, newPath);
            log.info("KC | TOPIC-X | entityId={} | path={} | BUFFERED (no KC user yet)",
                    entityId, newPath);

        } catch (JMSException e) {
            log.error("KcEntityBroadcastConsumer: message error applMsgId={}: {}",
                    applMsgId, e.getMessage());
            devLog.error("KcEntityBroadcastConsumer: message error applMsgId={}: {}",
                    applMsgId, e.getMessage(), e);
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

    private String extractPath(String textJson) {
        String ret = null;
        if (textJson != null && !textJson.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(textJson);
                JsonNode pathNode = node.get(EsqMsgConstants.TEXT_PATH);
                if (pathNode != null && !pathNode.isNull()) {
                    ret = pathNode.asText();
                }
            } catch (Exception e) {
                devLog.debug("KcEntityBroadcastConsumer: cannot parse textJson: {}", e.getMessage());
            }
        }
        return ret;
    }
}
