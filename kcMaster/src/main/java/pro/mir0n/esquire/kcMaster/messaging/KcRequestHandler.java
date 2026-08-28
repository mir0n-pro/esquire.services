/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — dispatches URQ to KC identity service by EventType
 * 04/06/2026 mir0n  EVENT_UPDATE_PATH dispatched to kcIdentityService.updateEntityPath()
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- handle() counts esq.biz.kc.sync.total and times esq.biz.kc.sync.duration
 *                   (tags op = the BusConstants command, outcome) in a finally; the switch is unchanged. A sync
 *                   that arrives and then FAILS leaves Esquire and KeyCloak disagreeing about who exists, and
 *                   nothing else reports that. The duration is the whole sync, not the admin client alone -- the
 *                   KC round-trip dominates it, and the name says what it measures rather than implying an
 *                   isolation that was not built. It is the ONLY view we have of an external dependency's latency
 * 07/17/2026 mir0n  note at the switch: esq.biz.kc.sync.duration is orthogonal to the bus-hop span pair -- the
 *                   KC request/response hop IS traced (PRODUCER/CONSUMER via AXRod), not a waterfall gap (I51).
 * 08/12/2026 mir0n  v1.2.13 -- KcSyncRequest -> AuthSyncRequest (moved to common backend.identity); @Component dropped --
 *                   the handler is built by KcIdentityGateway
 * 08/26/2026 mir0n  the updateAccess call drops the password and enabled arguments, neither of which the
 *                   messaging path manages
 */

package pro.mir0n.esquire.kcMaster.messaging;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
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
@RequiredArgsConstructor
public class KcRequestHandler {

    private final IKcIdentityService kcIdentityService;

    public void handle(String command, AuthSyncRequest req, String correlationId, String requestId) {
        // esq.biz.kc.sync.total / .duration (O1/T8 phase D): the identity sync's OUTCOME and how long it took.
        // The bus meters say a sync request arrived; only this says whether the identity in KeyCloak was actually
        // brought into line. A sync that arrives and then fails leaves Esquire and KeyCloak DISAGREEING about who
        // exists -- and nothing today reports that.
        //
        // The duration is measured HERE, around the whole sync, not inside the KeyCloak admin client. It is
        // therefore the sync's wall time, and the KC round-trip (an HTTP call to another service) dominates it --
        // the attribute mapping either side is microseconds against KeyCloak's milliseconds. Named for what it
        // measures rather than pretending to isolate the admin client. This is the ONLY view we have of an
        // external dependency's latency: KeyCloak is a separate server, and nothing else times it.
        //
        // NOT a stand-in for a bus-hop span (I51 REJECT). This meter is orthogonal to the tracing of the KC
        // request/response hop, which IS traced: the keySmith->kcMaster URQ is a MSG_TYPE_REQUEST (non-session),
        // so AXRod stamps a PRODUCER span on publish and runs this handler inside a CONSUMER span, joined by the
        // wire traceparent (messaging AXRod.transmit/receive). The only untraced leg is kcMaster->KeyCloak itself
        // -- stock KC is not OTel-instrumented, the I39 external boundary -- which is why this operation-grain
        // timer is the only latency view of it. Seeing this meter is not evidence the hop has no span pair.
        //
        // Tags bounded: op is the BusConstants command set, outcome is ok | error.
        String outcome = "error";
        long startedAt = System.nanoTime();
        try {
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
            outcome = "ok";
        } finally {
            EsqBizMeters.count("esq.biz.kc.sync.total", "op", String.valueOf(command), "outcome", outcome);
            EsqBizMeters.time("esq.biz.kc.sync.duration", System.nanoTime() - startedAt,
                    "op", String.valueOf(command));
        }
    }

    private void handleCreate(AuthSyncRequest req, String correlationId, String requestId) {
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

    private void handleUpdate(AuthSyncRequest req, String correlationId, String requestId) {
        String tfaMethod = req.getTfaMethod();
        Boolean requireTotp = "g".equals(tfaMethod) ? Boolean.TRUE : null;
        Boolean removeTotp  = "n".equals(tfaMethod) ? Boolean.TRUE : null;

        // NOTE: esq_uid (JWT_CLAIM_ENTITY_ID) is set at creation only — never updated
        kcIdentityService.updateUserAuthState(
                req.getLoginId(),
                req.getNewLoginId(),
                req.getEmail(),
                "Y".equals(req.getPwdChangeForced()),
                requireTotp,
                removeTotp,
                req.getRoles() != null ? req.getRoles() : Collections.emptyList(),
                null,   // no extra attributes on update
                correlationId,
                requestId
        );
    }

    private void handleDelete(AuthSyncRequest req, String correlationId, String requestId) {
        kcIdentityService.deleteUser(req.getLoginId(), correlationId, requestId);
    }

    private void handleUpdatePath(AuthSyncRequest req, String correlationId, String requestId) {
        kcIdentityService.updateEntityPath(req.getId(), req.getPath(), correlationId, requestId);
    }
}
