/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — replaces direct KC calls in KeySmithService.syncToKeycloak()
 *                   builds URQ from access profile JPA + roles and publishes to esquire.kc.request
 * 03/21/2026 mir0n  ctrlId injected from keysmith.messaging.ctrl-id config (@Value) — stable instance id,
 *                   not derived from correlationId; three-tier logging (msgLog/devLog), dual-mode msg audit
 * 03/26/2026 mir0n  MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING)
 * 06/14/2026 mir0n  bus-oriented: the URQ rides the x-Rod transport seam as a RodEvent (body = the sync fields)
 *                   to the {esquire.kc, kc-request} catalog leg, msg-type URQ. This keySmith instance's rod-id
 *                   (the leg's) rides the envelope so kcMaster routes the reply back to THIS instance's
 *                   kc-response selector. testReqId folded into the requestId (guaranteed non-null).
 * 06/15/2026 mir0n  the producer IXRod is opened from the shared XRodManager (ctor: rods.producer(BUS_KEY_KC,
 *                   Role.CLIENT)); publish() builds a RodEvent (msg-type URQ) and calls rod.transmit().
 *                   Dropped the JmsTemplate / ObjectMapper / ctrl-id @Value and the manual FIX-props send;
 *                   buildText() -> buildBody() returning the field Map (no JSON serialization here).
 */

package pro.mir0n.esquire.keySmith.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class KcSyncPublisher {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcSyncPublisher.class.getName());

    private final IXRod rod;

    public KcSyncPublisher(XRodManager rods) {
        this.rod = rods.producer(EsqMsgConstants.BUS_KEY_KC, Role.CLIENT);
    }

    /**
     * Publishes a URQ to kcMaster after the DB transaction has committed.
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
            Map<String, Object> body = buildBody(command, loginId, jpa, roles);
            String entityId = String.valueOf(jpa.getId());
            // guarantee a non-null tracking id (the former testReqId; it rides as the requestId on the wire).
            String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();

            RodEvent e = new RodEvent(RodEvent.opFromCode(command), EsqConstants.KIND_ACCESS_PROFILE, entityId, null,
                    System.currentTimeMillis(), correlationId, reqId, null, null, EsqMsgConstants.MSG_TYPE_REQUEST, body);
            rod.transmit(e);
            log.info("KC | URQ | {} | {} | {} | {}", command, EsqConstants.KIND_ACCESS_PROFILE, entityId, reqId);
        } catch (Exception e) {
            // DB already committed — message failure logged for reconciliation
            log.error("keySmith: failed to publish URQ: loginId={}, command={}, error={}", loginId, command, e.getMessage());
            devLog.error("keySmith: failed to publish URQ: loginId={}, command={}, requestId={}, correlationId={}, error={}", loginId, command, requestId, correlationId, e.getMessage(), e);
        }
    }

    private Map<String, Object> buildBody(String command, String loginId, EsqAccessProfileJpa jpa, List<EsqRoleJpa> roles) {
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

        return body;
    }

    private List<String> roleNames(List<EsqRoleJpa> roles) {
        return roles != null
                ? roles.stream().map(EsqRoleJpa::getName).toList()
                : List.of();
    }

}
