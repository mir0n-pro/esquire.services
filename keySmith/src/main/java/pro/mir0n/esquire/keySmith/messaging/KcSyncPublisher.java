/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — replaces direct KC calls in KeySmithService.syncToKeycloak()
 *                   builds URQ from access profile JPA + roles and publishes to esquire.kc.request
 * 03/21/2026 mir0n  ctrlId injected from keysmith.messaging.ctrl-id config (@Value) — stable instance id,
 *                   not derived from correlationId; three-tier logging (msgLog/devLog), dual-mode msg audit
 */

package pro.mir0n.esquire.keySmith.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KcSyncPublisher {

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + KcSyncPublisher.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcSyncPublisher.class.getName());

    @Value("${keysmith.messaging.ctrl-id}")
    private String ctrlId;

    private final JmsTemplate jmsQueueTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a URQ message to kcMaster after the DB transaction has committed.
     * Mirrors the three-branch logic previously in KeySmithService.syncToKeycloak().
     *
     * @param loginId        login id before the update (used for UPDATE/DELETE)
     * @param oldConnectFlg  connectFlg before the update
     * @param jpa            updated access profile
     * @param roles          assigned roles after update
     * @param correlationId  request correlation id
     * @param requestId      request trace id
     */
    public void publish(String loginId, String oldConnectFlg, EsqAccessProfileJpa jpa,
                        List<EsqRoleJpa> roles, String correlationId, String requestId) {
        String command;
        if ("Y".equals(oldConnectFlg) && "N".equals(jpa.getConnectFlg())) {
            command = EsqMsgConstants.EVENT_DELETE;
        } else if ("N".equals(oldConnectFlg) && "Y".equals(jpa.getConnectFlg())) {
            command = EsqMsgConstants.EVENT_CREATE;
        } else {
            command = EsqMsgConstants.EVENT_UPDATE;
        }

        try {
            String text      = buildText(command, loginId, jpa, roles);
            String entityId  = String.valueOf(jpa.getId());
            String testReqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();

            String mid = UUID.randomUUID().toString();

            Map<String, Object> props = new LinkedHashMap<>();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,     mid);
            props.put(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_REQUEST);
            props.put(EsqMsgConstants.FIELD_EVENT_TYPE,       command);
            props.put(EsqMsgConstants.FIELD_ENTITY_KIND,      EsqConstants.KIND_ACCESS_PROFILE);
            props.put(EsqMsgConstants.FIELD_ENTITY_ID,        entityId);
            props.put(EsqMsgConstants.FIELD_CTRL_ID,          ctrlId);
            props.put(EsqMsgConstants.FIELD_REQUEST_ID,       requestId);
            props.put(EsqMsgConstants.FIELD_CORRELATION_ID,   correlationId);
            props.put(EsqMsgConstants.FIELD_TEST_REQ_ID,      testReqId);
            props.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
            props.put(EsqMsgConstants.FIELD_TEXT,             text);

            jmsQueueTemplate.send(EsqMsgConstants.QUEUE_KC_REQUEST, (Session session) -> {
                Message msg = session.createMessage();
                Utils.setProps(msg, props);
                return msg;
            });

            if (msgLog.isDebugEnabled()) {
                msgLog.info("KC | URQ | {}", Utils.formatProps(props));
            } else {
                msgLog.info("KC | URQ | {} | {} | {} | {} | {} | {} | {} | {}",
                        mid, command, EsqConstants.KIND_ACCESS_PROFILE, entityId,
                        ctrlId, requestId, correlationId, testReqId);
            }
            log.info("KC | URQ | {} | {} | {} | {} | {} | {}",
                    mid, command, EsqConstants.KIND_ACCESS_PROFILE, entityId, ctrlId, testReqId);
        } catch (Exception e) {
            // DB already committed — message failure logged for reconciliation
            log.error("keySmith: failed to publish URQ: loginId={}, command={}, error={}", loginId, command, e.getMessage());
            devLog.error("keySmith: failed to publish URQ: loginId={}, command={}, requestId={}, correlationId={}, error={}", loginId, command, requestId, correlationId, e.getMessage(), e);
        }
    }

    private String buildText(String command, String loginId, EsqAccessProfileJpa jpa, List<EsqRoleJpa> roles) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id",   String.valueOf(jpa.getId()));
        body.put("kind", EsqConstants.KIND_ACCESS_PROFILE);

        switch (command) {
            case EsqMsgConstants.EVENT_DELETE -> {
                body.put("loginId", loginId);
            }
            case EsqMsgConstants.EVENT_CREATE -> {
                body.put("loginId",         jpa.getLoginId());
                body.put("email",           jpa.getEmail());
                body.put("pwdChangeForced", jpa.getPwdChangeForced());
                body.put("tfaMethod",       jpa.getTfaMethod());
                body.put("connectFlg",      jpa.getConnectFlg());
                body.put("path",            jpa.getPath());
                body.put("roles",           roleNames(roles));
            }
            case EsqMsgConstants.EVENT_UPDATE -> {
                body.put("loginId",         loginId);
                if (jpa.getLoginId() != null && !jpa.getLoginId().equals(loginId)) {
                    body.put("newLoginId",  jpa.getLoginId());
                }
                body.put("email",           jpa.getEmail());
                body.put("pwdChangeForced", jpa.getPwdChangeForced());
                body.put("tfaMethod",       jpa.getTfaMethod());
                body.put("connectFlg",      jpa.getConnectFlg());
                body.put("roles",           roleNames(roles));
            }
        }

        return objectMapper.writeValueAsString(body);
    }

    private List<String> roleNames(List<EsqRoleJpa> roles) {
        return roles != null
                ? roles.stream().map(EsqRoleJpa::getName).toList()
                : List.of();
    }

}
