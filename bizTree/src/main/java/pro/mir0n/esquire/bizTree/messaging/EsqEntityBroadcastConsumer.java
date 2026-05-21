/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
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
 * 05/20/2026 mir0n  Taijitu refactor (v1.2.5): reduced to a thin pass-through to
 *                   IBizTreeDirector; per-kind handler dispatch extracted to
 *                   MessageHandlerHub (now behind the director); parses textJson once
 *                   and forwards via director.onEntityBroadcast()
 * 05/20/2026 mir0n  generalization: no longer parses -- forwards the RAW body
 *                   (messageEncoding + textJson) plus requestId / correlationId via the 7-arg
 *                   director.onEntityBroadcast(); ObjectMapper field + readTree block removed
 *                   (the director parses: worker thread for yang/taijitu, inline for legacy).
 */
package pro.mir0n.esquire.bizTree.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

/**
 * Stable JMS entry point for bizTree. Thin pass-through: pulls the message
 * properties off the wire, parses textJson once, and hands a structured
 * event to {@link IBizTreeDirector}. Holds zero dispatch logic -- per-kind
 * handler routing lives behind the director (today in MessageHandlerHub
 * inside BizTreeDirectorLegacy; tomorrow inside each Taijitu Monad).
 *
 * Subscription:
 *   - clientId: biztree.messaging.client-id (BizTreeJmsConfig)
 *   - subscriptionName: esquire.entity.broadcast.biztree.primary (stable, do not change)
 *   - selector: BusID = 'esquire.entity' AND MsgType = 'UE'
 *   - idempotency key: ApplMsgID
 *
 * Cache implementation changes (Step 3 swap to Taijitu) leave this file
 * untouched. The director interface is the public contract; this class
 * only marshalls JMS into that contract.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "biztree.messaging.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class EsqEntityBroadcastConsumer {

    private static final Logger msgLog = LoggerFactory.getLogger("msg."     + EsqEntityBroadcastConsumer.class.getName());
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityBroadcastConsumer.class.getName());

    private static final String SUBSCRIPTION_NAME =
            EsqMsgConstants.TOPIC_ENTITY_BROADCAST + ".biztree.primary";
    private static final String MSG_SELECTOR =
            EsqMsgConstants.FIELD_BUS_ID   + " = '" + EsqMsgConstants.BUS_ID_ENTITY              + "'" +
            " AND " +
            EsqMsgConstants.FIELD_MSG_TYPE + " = '" + EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS + "'";

    private final IBizTreeDirector director;

    public EsqEntityBroadcastConsumer(IBizTreeDirector director) {
        this.director = director;
    }

    @JmsListener(
        destination      = EsqMsgConstants.TOPIC_ENTITY_BROADCAST,
        containerFactory = "jmsDurableTopicListenerFactory",
        subscription     = SUBSCRIPTION_NAME,
        selector         = MSG_SELECTOR
    )
    public void onEntityBroadcast(Message message) {
        String applMsgId = null;
        try {
            applMsgId            = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind    = message.getIntProperty(   EsqMsgConstants.FIELD_ENTITY_KIND);
            String eventType     = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String encoding      = message.getStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING);
            String textJson      = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            MDC.put(EsqConstants.PD_REQUEST_ID,     requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            if (msgLog.isDebugEnabled()) {
                msgLog.info("ENTITY | UE | {}", Utils.formatProps(message));
            } else {
                msgLog.info("ENTITY | UE | {} | {} | {} | {} | {} | {}",
                        applMsgId, eventType, entityKind, entityId, requestId, correlationId);
            }
            log.info("ENTITY | UE | {} | {} | {} | {}",
                    applMsgId, eventType, entityKind, entityId); // requestId, correlationId in MDC

            // Pass the raw body through (no parse here); the director parses it -- on the
            // worker thread for yang/taijitu, inline for legacy.
            director.onEntityBroadcast(eventType, entityId, entityKind,
                    requestId, correlationId, encoding, textJson);

        } catch (JMSException e) {
            log.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage());
            devLog.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}
