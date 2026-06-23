/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: the keySmith end of the kc bus (CLIENT) -- one adapter, both legs. Merges the former
 *                   KcSyncPublisher (transmit URQ) + KcSyncResponseListener (receive URS/URR) onto the single
 *                   kc-CLIENT rod (from the facade). publish() builds the URQ from the access profile + roles and
 *                   transmits it; onResponse() handles the reply tagged with this instance's rod-id.
 */
package pro.mir0n.esquire.keySmith.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The keySmith end of the kc bus (CLIENT role): one rod, both legs. {@link #publish} transmits a URQ to kcMaster
 * after the DB transaction commits; {@link #onResponse} receives the URS/URR reply for THIS keySmith instance
 * (the rod-id selector isolates it).
 */
@Slf4j
@Component
public class KcBusAdapter {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcBusAdapter.class.getName());

    private final IXRod rod;

    public KcBusAdapter() {
        // kc CLIENT: transmit URQ requests + receive URS/URR responses (rod-id selector), on one rod.
        this.rod = MessagingBus.getInstance().getXRod(EsqMsgConstants.BUS_KEY_KC);
        this.rod.setWorker(this::onResponse);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);                // role-support: probe -- throws if the rod has no transmit leg
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
            // DB already committed -- message failure logged for reconciliation
            log.error("keySmith: failed to publish URQ: loginId={}, command={}, error={}", loginId, command, e.getMessage());
            devLog.error("keySmith: failed to publish URQ: loginId={}, command={}, requestId={}, correlationId={}, error={}", loginId, command, requestId, correlationId, e.getMessage(), e);
        }
    }

    /** Receive the KC sync response (URS/URR) for this keySmith instance off the kc-response leg. */
    void onResponse(RodEvent e) {
        MDC.put(EsqConstants.PD_REQUEST_ID, e.requestId());
        MDC.put(EsqConstants.PD_CORRELATION_ID, e.correlationId());
        try {
            // msgType is the authoritative URS/URR tag (no need to inspect the body).
            log.info("KC | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId());
            // todo: correlate by requestId; update sync status / reconciliation record
        } finally {
            MDC.clear();
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
