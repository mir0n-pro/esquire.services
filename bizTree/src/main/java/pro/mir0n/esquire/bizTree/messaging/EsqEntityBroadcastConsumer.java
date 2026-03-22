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
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

/**
 * Durable consumer for the esquire.entity.broadcast topic.
 *
 * Phase 1: logs received messages to the dedicated entity-broadcast log file.[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:testCompile (default-testCompile) on project esquire-biz-tree: Compilation failure
 * [ERROR] /C:/MyProjects/esquire/services/bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java:[41,17] constructor Repo in record pro.mir0n.esquire.bizTree.cache.BizTreeCacheSql.Repo cannot be applied to given types;
 * [ERROR]   required: java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String
 * [ERROR]   found:    java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String
 * [ERROR]   reason: actual and formal argument lists differ in length
 * [ERROR]
 * [ERROR] -> [Help 1]                                                                                                                                                           3:02 PM[ERROR]                                                                                                                                                                              [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.                                                                                                  [ERROR] Re-run Maven using the -X switch to enable full debug log
 * Phase 2: UPDATE events are applied to the cache via IBizTreeCacheRepository.updateNode().
 * Enable via: biztree.messaging.consumer.enabled=true (default: true).
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

    private static final int STATUS_OK      = 0;
    private static final int STATUS_DELETED = 1;
    private static final int STATUS_LOCKED  = 2;

    private static final Logger msgLog = LoggerFactory.getLogger("msg." + EsqEntityBroadcastConsumer.class.getName());
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityBroadcastConsumer.class.getName());

    private final IBizTreeCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public EsqEntityBroadcastConsumer(IBizTreeCacheRepository cacheRepository, ObjectMapper objectMapper) {
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
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
            applMsgId         = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String serviceId  = message.getStringProperty(EsqMsgConstants.FIELD_SERVICE_ID);
            String entityId   = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String eventType  = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String requestId  = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String textJson   = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

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

            if (EsqMsgConstants.EVENT_UPDATE.equals(eventType) && textJson != null) {
                try {
                    JsonNode textNode  = objectMapper.readTree(textJson);
                    boolean  hasName    = textNode.has("name") && !textNode.get("name").isNull();
                    boolean  hasDesc    = textNode.has("desc");  // can be null
                    boolean  hasStatus  = textNode.has("status") && !textNode.get("status").isNull();   // acc_status (pacMan/ACCT)
                    boolean  hasDeleted = textNode.has("deleted") && !textNode.get("deleted").isNull();  // usr_deleted_flg (enyMan/USR)
                    if (hasName || hasDesc || hasStatus || hasDeleted) {
                        long    pk         = Long.parseLong(entityId);
                        String  name       = hasName    ? textNode.get("name").asText() : null;
                        String  desc       = hasDesc    ? (textNode.get("desc").isNull()    ? null : textNode.get("desc").asText()) : IBizTreeCacheRepository.SKIP;
                        Integer statusCode = null;
                        if (hasStatus) {
                            statusCode = decodeStatus(textNode.get("status").asText());
                        } else if (hasDeleted) {
                            statusCode = decodeStatus(textNode.get("deleted").asText());
                        }
                        cacheRepository.updateNode(pk, name, desc, statusCode);
                    }
                } catch (Exception ex) {
                    log.error("EsqEntityBroadcastConsumer: cache update failed applMsgId={}: {}", applMsgId, ex.getMessage());
                    devLog.error("EsqEntityBroadcastConsumer: cache update failed applMsgId={}: {}", applMsgId, ex.getMessage(), ex);
                }
            }

        } catch (JMSException e) {
            log.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage());
            devLog.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    // usr_deleted_flg: Y/C → deleted(1), L → locked(2), null/other → ok(0)
    // acc_status:      C   → deleted(1), L → locked(2), O/null/other → ok(0)
    private static int decodeStatus(String raw) {
        int ret = STATUS_OK;
        if ("Y".equals(raw) || "C".equals(raw)){
            ret = STATUS_DELETED;
        } else if ("L".equals(raw)) {
            ret = STATUS_LOCKED;
        }
        return ret;
    }
}
