/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith KeycloakIdentityService
 *                   @Async removed -- KC calls are synchronous here so handler can publish URS after completion
 * 03/21/2026 mir0n  three-tier logging: kcAudit->devLog; KC state events (STARTED/SUCCESS) to log.info;
 *                   all log.debug->devLog.debug; unused imports removed
 * 04/06/2026 mir0n  updateEntityPath(): looks up KC user by esq_uid attribute, updates esq_rootpath
 *                   updateUser(): removed changed-flag guard -- attributes always merged and applied
 * 04/16/2026 mir0n  updateEntityPath(), syncRoles(): null-guard replaces early returns; for-loops replace streams; explicit types replace var
 * 06/02/2026 mir0n  race-8c (v1.2.6 Goal 3): KcPathBuffer injected; createUser() flushes the buffer --
 *                   consume(entityId) after the KC user is created and applyBufferedPath() writes
 *                   esq_rootpath when it differs; updateEntityPath() no-KC-user branch no longer
 *                   buffers (request side just skips -- the X topic message feeds the buffer)
 * 07/08/2026 mir0n  @EsqTraced on createUser / updateUserAuthState / deleteUser
 *                   (esq.kc.create-user / esq.kc.update-auth / esq.kc.delete-user)
 * 07/09/2026 mir0n  v1.2.11 -- @EsqTraced labels "KC ..." -> "Keycloak ..."; @EsqTraced on updateUserPath
 *                   (esq.kc.update-path, "Keycloak update path")
 * 08/11/2026 mir0n  v1.2.12 -- createUser consumes a ParkedPath from the shared ExpiringCache and applies
 *                   its path
 * 08/12/2026 mir0n  v1.2.13 -- @Service dropped -- built by KcIdentityGateway; KcSyncRequest -> AuthSyncRequest
 * 08/26/2026 mir0n  updateAccess reads the user existing required actions and holds each one both ways via
 *                   holdAction: UPDATE_PASSWORD follows forcePasswordChange, CONFIGURE_TOTP is set on
 *                   request and removed on removeTotp, and the write happens only when something changed.
 *                   deleteUser checks and closes the JAX-RS Response, so a refused delete is not SUCCESS
 * 11/21/2026 mir0n  v2.0.2 -- consume -> getAndRemove
 * 11/23/2026 mir0n  v2.0.3 -- pure OAuth only: the path park field and the race-8c flush (applyBufferedPath())
 *                   out of createUser(); the realm role sync (updateRealmRoles(), assignRealmRoles()) and
 *                   updateEntityPath() out
 * 11/25/2026 mir0n  v2.0.3 -- createUser() and updateUserAuthState() lose attributes: the attribute set on a
 *                   create and the merge on an update out
 */

package pro.mir0n.esquire.kcMaster.service.impl;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import pro.mir0n.esquire.backend.o11y.EsqTraced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;
import pro.mir0n.esquire.kcMaster.service.IKcIdentityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class KcIdentityService implements IKcIdentityService {
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcIdentityService.class.getName());

    private final Keycloak keycloak;
    private final KcConnectionSettings kcConnection;

    @Override
    @EsqTraced(name = "esq.kc.create-user", label = "Keycloak create user")
    public void createUser(
            String loginId,
            String email,
            String password,
            boolean enabled,
            boolean forcePasswordChange,
            boolean requireTotp,
            String correlationId,
            String requestId
    ) {
        log.info("KC | CREATE | username={} | state=STARTED", loginId);
        devLog.debug("Creating user in Keycloak: username={}, email={}", loginId, email);

        RealmResource realmResource = keycloak.realm(kcConnection.getRealm());
        UsersResource usersResource = realmResource.users();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(loginId);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setEmailVerified(false);

        if (forcePasswordChange) {
            user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
        }

        if (requireTotp) {
            List<String> requiredActions = user.getRequiredActions() != null
                    ? new ArrayList<>(user.getRequiredActions())
                    : new ArrayList<>();
            requiredActions.add("CONFIGURE_TOTP");
            user.setRequiredActions(requiredActions);
        }

        String kcId;
        Response response = usersResource.create(user);
        try {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user in Keycloak: " + response.getStatusInfo());
            }
            kcId = extractUserIdFromResponse(response);
        } finally {
            response.close();
        }

        if (password != null && !password.isEmpty()) {
            setUserPassword(usersResource, kcId, password, !forcePasswordChange);
        }

        log.info("KC | CREATE | username={} | state=SUCCESS | kcUserId={}", loginId, kcId);
    }

    @Override
    @EsqTraced(name = "esq.kc.update-auth", label = "Keycloak update auth state")
    public void updateUserAuthState(
            String loginId,
            String newLoginId,
            String email,
            Boolean forcePasswordChange,
            Boolean requireTotp,
            Boolean removeTotp,
            String correlationId,
            String requestId
    ) {
        log.info("KC | UPDATE | username={} | state=STARTED", loginId);
        devLog.debug("Updating user auth state in Keycloak: username={}", loginId);

        RealmResource realmResource = keycloak.realm(kcConnection.getRealm());
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.search(loginId, true);
        if (users.isEmpty()) {
            throw new NotFoundException("User not found: " + loginId);
        }

        String kcId = users.get(0).getId();
        UserResource userResource = usersResource.get(kcId);
        // Full representation required -- search result carries minimal data (attributes = null).
        // Using toRepresentation() preserves the existing KC attributes
        UserRepresentation user = userResource.toRepresentation();

        boolean changed = false;

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        List<String> requiredActions = new ArrayList<>();
        if (user.getRequiredActions() != null) {
            requiredActions.addAll(user.getRequiredActions());
        }
        boolean actionsChanged = false;
        if (forcePasswordChange != null) {
            actionsChanged = holdAction(requiredActions, "UPDATE_PASSWORD", forcePasswordChange);
        }
        if (Boolean.TRUE.equals(requireTotp)) {
            actionsChanged = holdAction(requiredActions, "CONFIGURE_TOTP", true) || actionsChanged;
        }
        if (Boolean.TRUE.equals(removeTotp)) {
            actionsChanged = holdAction(requiredActions, "CONFIGURE_TOTP", false) || actionsChanged;
        }
        if (actionsChanged) {
            user.setRequiredActions(requiredActions);
            changed = true;
        }

        if (newLoginId != null && !newLoginId.equals(loginId)) {
            user.setUsername(newLoginId);
            changed = true;
        }

        if (changed) {
            userResource.update(user);
            devLog.debug("User updated: {}", loginId);
        } else {
            devLog.debug("User representation unchanged, skipping KC update: {}", loginId);
        }

        if (Boolean.TRUE.equals(removeTotp)) {
            for (CredentialRepresentation c : userResource.credentials()) {
                if ("otp".equals(c.getType())) {
                    userResource.removeCredential(c.getId());
                }
            }
            log.info("KC | UPDATE | username={} | OTP credentials removed", loginId);
        }

        log.info("KC | UPDATE | username={} | state=SUCCESS", loginId);
    }

    @Override
    @EsqTraced(name = "esq.kc.delete-user", label = "Keycloak delete user")
    public void deleteUser(String loginId, String correlationId, String requestId) {
        log.info("KC | DELETE | username={} | state=STARTED", loginId);
        devLog.debug("Deleting user from Keycloak: username={}", loginId);

        RealmResource realmResource = keycloak.realm(kcConnection.getRealm());
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.search(loginId, true);
        if (users.isEmpty()) {
            throw new NotFoundException("User not found: " + loginId);
        }

        String kcId = users.get(0).getId();
        //xxx: delete() hands back a Response and throws nothing -- an unchecked one reads as SUCCESS.
        Response response = usersResource.delete(kcId);
        try {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new RuntimeException("Failed to delete user in Keycloak: " + response.getStatusInfo());
            }
        } finally {
            response.close();
        }

        log.info("KC | DELETE | username={} | state=SUCCESS", loginId);
        devLog.debug("User deleted: {}", kcId);
    }

    private String extractUserIdFromResponse(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null) {
            throw new RuntimeException("Location header not found in create user response");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void setUserPassword(UsersResource usersResource, String kcId, String password, boolean permanent) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(!permanent);
        usersResource.get(kcId).resetPassword(credential);
        devLog.debug("Password set for user: {}", kcId);
    }

    private static boolean holdAction(List<String> actions, String action, boolean wanted) {
        boolean ret = false;
        boolean present = actions.contains(action);
        if (wanted && !present) {
            actions.add(action);
            ret = true;
        } else if (!wanted && present) {
            actions.remove(action);
            ret = true;
        }
        return ret;
    }
}
