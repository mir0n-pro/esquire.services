/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: durable subscriber on esquire.entity.broadcast; Phase 1 logs received messages
 * 03/20/2026 mir0n  Phase 2: UPDATE events applied to H2 cache via IBizTreeCacheRepository.updateNode()
 *                   handles "deleted" (enyMan/USR) and "status" (pacMan/ACCT) fields
 *                   decodeStatus(): raw string → 0/1/2; null status values not propagated
 * 03/21/2026 mir0n  three-tier logging: broadcastLog→msgLog/devLog; MDC set/clear; requestId/correlationId reads;
 *                   dual-mode ENTITY msg audit; console echo log.info; dual error pattern; unused imports removed
 * 03/25/2026 mir0n  handler map dispatch: HandlerKey(eventType,kindBits) → IBizTreeEventHandler;
 *                   kindBits via EsqObjectKindStorage (isAcct?4:0)+(isUsr?2:0)+(isOrg?1:0);
 *                   UpdateEntityHandler/CreateOrgHandler/CreateUsrHandler extracted to handler/ package;
 *                   KindType enum removed
 * 03/26/2026 mir0n  DeleteEntityHandler registered for (DELETE, ORG/USR/ACCT) — skeleton, no cascade
 * 04/02/2025 mir0n  Added 3 move handlers for each kind-kind
 * 04/07/2026 mir0n  kind normalization removed from dispatch; EsqObjectKindStorage.get() receives raw entityKind
 * 04/16/2026 mir0n  kindBits: ternary expression expanded to explicit if-assignments
 */
package pro.mir0n.esquire.bizTree.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.handler.*;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Durable consumer for the esquire.entity.broadcast topic.
 *
 * Dispatches incoming entity events via a Map of IBizTreeEventHandler implementations,
 * keyed by (eventType, kindBits) where kindBits = (isAcct?4:0)+(isUsr?2:0)+(isOrg?1:0).
 *
 * Durable subscription:
 *   - clientId: biztree.messaging.client-id (BizTreeJmsConfig)
 *   - subscriptionName: esquire.entity.broadcast.biztree.primary (stable, do not change)
 *   - selector: BusID = 'esquire.entity' AND MsgType = 'UE'
 *   - idempotency key: ApplMsgID
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "biztree.messaging.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class EsqEntityBroadcastConsumer {

    private static final Logger msgLog = LoggerFactory.getLogger("msg." + EsqEntityBroadcastConsumer.class.getName());
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityBroadcastConsumer.class.getName());

    private record HandlerKey(String eventType, int kindBits) {}

    private final IBizTreeCacheRepository          cacheRepository;
    private final ObjectMapper                     objectMapper;
    private final Map<HandlerKey, IBizTreeEventHandler> handlers;

    public EsqEntityBroadcastConsumer(IBizTreeCacheRepository cacheRepository, ObjectMapper objectMapper) {
        this.cacheRepository = cacheRepository;
        this.objectMapper    = objectMapper;

        UpdateEntityHandler updateHandler = new UpdateEntityHandler(cacheRepository);
        DeleteEntityHandler deleteHandler = new DeleteEntityHandler(cacheRepository);
        handlers = new HashMap<>();
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE, 1), updateHandler);  // ORG
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE, 2), updateHandler);  // USR
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE, 4), updateHandler);  // ACCT
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_CREATE, 1), new CreateOrgHandler(cacheRepository));
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_CREATE, 2), new CreateUsrHandler(cacheRepository));
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_CREATE, 4), new CreateAcctHandler(cacheRepository));
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_DELETE, 1), deleteHandler);  // ORG
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_DELETE, 2), deleteHandler);  // USR
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_DELETE, 4), deleteHandler);  // ACCT
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE_PATH, 1), new MoveOrgHandler(cacheRepository));
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE_PATH, 2), new MoveUsrHandler(cacheRepository));
        handlers.put(new HandlerKey(EsqMsgConstants.EVENT_UPDATE_PATH, 4), new MoveAcctHandler(cacheRepository));
    }

    private static final String SUBSCRIPTION_NAME =
            EsqMsgConstants.TOPIC_ENTITY_BROADCAST + ".biztree.primary";
    private static final String MSG_SELECTOR =
            EsqMsgConstants.FIELD_BUS_ID  + " = '" + EsqMsgConstants.BUS_ID_ENTITY              + "'" +
            " AND " +
            EsqMsgConstants.FIELD_MSG_TYPE + " = '" + EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS + "'";

    @JmsListener(
        destination = EsqMsgConstants.TOPIC_ENTITY_BROADCAST,
        containerFactory = "jmsDurableTopicListenerFactory",
        subscription = SUBSCRIPTION_NAME,
        selector = MSG_SELECTOR
    )
    public void onEntityBroadcast(Message message) {
        String applMsgId = null;
        try {
            applMsgId            = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String serviceId     = message.getStringProperty(EsqMsgConstants.FIELD_SERVICE_ID);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind    = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String eventType     = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String textJson      = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            if (msgLog.isDebugEnabled()) {
                msgLog.info("ENTITY | UE | {}", Utils.formatProps(message));
            } else {
                msgLog.info("ENTITY | UE | {} | {} | {} | {} | {} | {}",
                        applMsgId, eventType, entityKind, entityId, requestId, correlationId);
            }
            log.info("ENTITY | UE | {} | {} | {} | {}",
                    applMsgId, eventType, entityKind, entityId); //xxx: requestId, correlationId are in MDC

            EsqObjectKind eek      = EsqObjectKindStorage.getInstance().get(entityKind);
            int kindBits = 0;
            if (eek.isAcct()) kindBits += 4;
            if (eek.isUsr())  kindBits += 2;
            if (eek.isOrg())  kindBits += 1;

            IBizTreeEventHandler handler = handlers.get(new HandlerKey(eventType, kindBits));
            if (handler != null && textJson != null) {
                try {
                    JsonNode textNode = objectMapper.readTree(textJson);
                    handler.handle(entityId, entityKind, textNode);
                } catch (Exception ex) {
                    log.error("EsqEntityBroadcastConsumer: handler failed applMsgId={} eventType={} kind={}: {}",
                            applMsgId, eventType, entityKind, ex.getMessage());
                    devLog.error("EsqEntityBroadcastConsumer: handler failed applMsgId={} eventType={} kind={}: {}",
                            applMsgId, eventType, entityKind, ex.getMessage(), ex);
                }
            }

        } catch (JMSException e) {
            log.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage());
            devLog.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}
