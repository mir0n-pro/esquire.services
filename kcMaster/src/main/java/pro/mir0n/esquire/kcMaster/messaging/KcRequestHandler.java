/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — dispatches URQ to KC identity service by EventType
 * 04/06/2026 mir0n  EVENT_UPDATE_PATH dispatched to kcIdentityService.updateEntityPath()
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 */

package pro.mir0n.esquire.kcMaster.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.kcMaster.service.IKcIdentityService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a deserialized URQ to the appropriate KC identity operation.
 * Returns success/failure to caller; caller (KcRequestConsumer) publishes URS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KcRequestHandler {

    private final IKcIdentityService kcIdentityService;

    public void handle(String command, KcSyncRequest req, String correlationId, String requestId) {
        switch (command) {
            case BusConstants.EVENT_CREATE ->
                handleCreate(req, correlationId, requestId);
            case BusConstants.EVENT_UPDATE ->
                handleUpdate(req, correlationId, requestId);
            case BusConstants.EVENT_DELETE ->
                handleDelete(req, correlationId, requestId);
            case BusConstants.EVENT_UPDATE_PATH ->
                handleUpdatePath(req, correlationId, requestId);
            default ->
                throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private void handleCreate(KcSyncRequest req, String correlationId, String requestId) {
        Map<String, List<String>> attributes = new java.util.HashMap<>();
        attributes.put(EsqConstants.JWT_CLAIM_ENTITY_ID,       Collections.singletonList(req.getId()));
        attributes.put(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, Collections.singletonList(req.getPath()));

        kcIdentityService.createUser(
                req.getLoginId(),
                req.getEmail(),
                "changeit",   // temporary password
                true,         // enabled
                true,         // forcePasswordChange — must change on first login
                false,        // requireTotp — N on initial connect
                req.getRoles() != null ? req.getRoles() : Collections.emptyList(),
                attributes,
                correlationId,
                requestId
        );
    }

    private void handleUpdate(KcSyncRequest req, String correlationId, String requestId) {
        String tfaMethod = req.getTfaMethod();
        Boolean requireTotp = "g".equals(tfaMethod) ? Boolean.TRUE : null;
        Boolean removeTotp  = "n".equals(tfaMethod) ? Boolean.TRUE : null;

        // NOTE: esq_uid (JWT_CLAIM_ENTITY_ID) is set at creation only — never updated
        kcIdentityService.updateUserAuthState(
                req.getLoginId(),
                req.getNewLoginId(),
                req.getEmail(),
                null,   // password — not managed via messaging
                null,   // enabled — not managed here
                "Y".equals(req.getPwdChangeForced()),
                requireTotp,
                removeTotp,
                req.getRoles() != null ? req.getRoles() : Collections.emptyList(),
                null,   // no extra attributes on update
                correlationId,
                requestId
        );
    }

    private void handleDelete(KcSyncRequest req, String correlationId, String requestId) {
        kcIdentityService.deleteUser(req.getLoginId(), correlationId, requestId);
    }

    private void handleUpdatePath(KcSyncRequest req, String correlationId, String requestId) {
        kcIdentityService.updateEntityPath(req.getId(), req.getPath(), correlationId, requestId);
    }
}
