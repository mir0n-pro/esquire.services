/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith KeycloakIdentityService
 *                   @Async removed — KC calls are synchronous here so handler can publish URS after completion
 * 03/21/2026 mir0n  three-tier logging: kcAudit→devLog; KC state events (STARTED/SUCCESS) to log.info;
 *                   all log.debug→devLog.debug; unused imports removed
 * 04/06/2026 mir0n  updateEntityPath(): looks up KC user by esq_uid attribute, updates esq_rootpath
 *                   updateUser(): removed changed-flag guard — attributes always merged and applied
 * 04/16/2026 mir0n  updateEntityPath(), syncRoles(): null-guard replaces early returns; for-loops replace streams; explicit types replace var
 * 06/02/2026 mir0n  race-8c (v1.2.6 Goal 3): KcPathBuffer injected; createUser() flushes the buffer --
 *                   consume(entityId) after the KC user is created and applyBufferedPath() writes
 *                   esq_rootpath when it differs; updateEntityPath() no-KC-user branch no longer
 *                   buffers (request side just skips -- the X topic message feeds the buffer)
 */

package pro.mir0n.esquire.kcMaster.service.impl;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;
import pro.mir0n.esquire.kcMaster.service.IKcIdentityService;

import pro.mir0n.esquire.common.EsqConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KcIdentityService implements IKcIdentityService {
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcIdentityService.class.getName());

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;
    private final KcPathBuffer pathBuffer;

    @Override
    public void createUser(
            String loginId,
            String email,
            String password,
            boolean enabled,
            boolean forcePasswordChange,
            boolean requireTotp,
            List<String> realmRoles,
            Map<String, List<String>> attributes,
            String correlationId,
            String requestId
    ) {
        log.info("KC | CREATE | username={} | state=STARTED", loginId);
        devLog.debug("Creating user in Keycloak: username={}, email={}", loginId, email);

        RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
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

        if (attributes != null && !attributes.isEmpty()) {
            user.setAttributes(attributes);
        }

        Response response = usersResource.create(user);
        if (response.getStatus() != 201) {
            String errorMsg = "Failed to create user in Keycloak: " + response.getStatusInfo();
            response.close();
            throw new RuntimeException(errorMsg);
        }

        String kcId = extractUserIdFromResponse(response);

        if (password != null && !password.isEmpty()) {
            setUserPassword(usersResource, kcId, password, !forcePasswordChange);
        }

        if (realmRoles != null && !realmRoles.isEmpty()) {
            assignRealmRoles(realmResource, kcId, realmRoles);
        }

        response.close();

        // Race-8c flush: if enyMan's move-cascade EVENT_UPDATE_PATH for this entity
        // arrived on the topic before the user existed, KcEntityBroadcastConsumer
        // parked the post-move path in KcPathBuffer. Consume it now and apply --
        // otherwise the URQ EVENT_UPDATE_PATH path was silent-skipped and the KC
        // user would be left with the stale CREATE-time path forever.
        String entityId = (attributes != null && attributes.get(EsqConstants.JWT_CLAIM_ENTITY_ID) != null
                && !attributes.get(EsqConstants.JWT_CLAIM_ENTITY_ID).isEmpty())
                ? attributes.get(EsqConstants.JWT_CLAIM_ENTITY_ID).get(0) : null;
        if (entityId != null) {
            String bufferedPath = pathBuffer.consume(entityId);
            if (bufferedPath != null) {
                applyBufferedPath(usersResource, kcId, entityId, bufferedPath);
            }
        }

        log.info("KC | CREATE | username={} | state=SUCCESS | kcUserId={}", loginId, kcId);
    }

    private void applyBufferedPath(UsersResource usersResource, String kcId, String entityId, String newPath) {
        UserResource userResource = usersResource.get(kcId);
        UserRepresentation user = userResource.toRepresentation();
        Map<String, List<String>> existing = user.getAttributes() != null
                ? user.getAttributes() : Collections.emptyMap();
        List<String> currentPaths = existing.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH);
        String currentPath = (currentPaths != null && !currentPaths.isEmpty()) ? currentPaths.get(0) : null;
        if (!newPath.equals(currentPath)) {
            Map<String, List<String>> merged = new HashMap<>(existing);
            merged.put(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, Collections.singletonList(newPath));
            user.setAttributes(merged);
            userResource.update(user);
            log.info("KC | CREATE | entityId={} | buffered-path-applied={}", entityId, newPath);
        }
    }

    @Override
    public void updateUserAuthState(
            String loginId,
            String newLoginId,
            String email,
            String password,
            Boolean enabled,
            Boolean forcePasswordChange,
            Boolean requireTotp,
            Boolean removeTotp,
            List<String> realmRoles,
            Map<String, List<String>> attributes,
            String correlationId,
            String requestId
    ) {
        log.info("KC | UPDATE | username={} | state=STARTED", loginId);
        devLog.debug("Updating user auth state in Keycloak: username={}", loginId);

        RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.search(loginId, true);
        if (users.isEmpty()) {
            throw new NotFoundException("User not found: " + loginId);
        }

        String kcId = users.get(0).getId();
        UserResource userResource = usersResource.get(kcId);
        // Full representation required — search result carries minimal data (attributes = null).
        // Using toRepresentation() preserves existing KC attributes (esq_uid, esq_rootpath, etc.)
        UserRepresentation user = userResource.toRepresentation();

        boolean changed = false;

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        List<String> requiredActions = new ArrayList<>();
        if (Boolean.TRUE.equals(forcePasswordChange)) {
            requiredActions.add("UPDATE_PASSWORD");
        }
        if (Boolean.TRUE.equals(requireTotp)) {
            requiredActions.add("CONFIGURE_TOTP");
        }
        if (!requiredActions.isEmpty()) {
            user.setRequiredActions(requiredActions);
            changed = true;
        }

        if (newLoginId != null && !newLoginId.equals(loginId)) {
            user.setUsername(newLoginId);
            changed = true;
        }

        // Merge attributes — never replace whole map, preserves esq_uid, esq_rootpath, etc.
        if (attributes != null) {
            Map<String, List<String>> merged = new HashMap<>();
            if (user.getAttributes() != null) {
                merged.putAll(user.getAttributes());
            }
            merged.putAll(attributes);
            user.setAttributes(merged);
            changed = true;
        }

        if (changed) {
            userResource.update(user);
            devLog.debug("User updated: {}", loginId);
        } else {
            devLog.debug("User representation unchanged, skipping KC update: {}", loginId);
        }

        if (realmRoles != null) {
            updateRealmRoles(realmResource, kcId, realmRoles);
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
    public void deleteUser(String loginId, String correlationId, String requestId) {
        log.info("KC | DELETE | username={} | state=STARTED", loginId);
        devLog.debug("Deleting user from Keycloak: username={}", loginId);

        RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.search(loginId, true);
        if (users.isEmpty()) {
            throw new NotFoundException("User not found: " + loginId);
        }

        String kcId = users.get(0).getId();
        usersResource.delete(kcId);

        log.info("KC | DELETE | username={} | state=SUCCESS", loginId);
        devLog.debug("User deleted: {}", kcId);
    }

    @Override
    public void updateEntityPath(String entityId, String newPath, String correlationId, String requestId) {
        log.info("KC | MOVE | entityId={} | state=STARTED", entityId);

        RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.searchByAttributes(
                EsqConstants.JWT_CLAIM_ENTITY_ID + ":" + entityId, true);
        if (!users.isEmpty()) {
            String kcId = users.get(0).getId();
            UserResource userResource = usersResource.get(kcId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> existing = user.getAttributes() != null ? user.getAttributes() : Collections.emptyMap();
            List<String> currentPaths = existing.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH);
            String currentPath = (currentPaths != null && !currentPaths.isEmpty()) ? currentPaths.get(0) : null;
            if (!newPath.equals(currentPath)) {
                Map<String, List<String>> merged = new HashMap<>(existing);
                merged.put(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, Collections.singletonList(newPath));
                user.setAttributes(merged);
                userResource.update(user);
                devLog.debug("Path reset for user: {}='{}'", entityId, newPath);
                log.info("KC | MOVE | entityId={} | state=SUCCESS", entityId);
            } else {
                devLog.debug("KC | MOVE | entityId={} : path unchanged, skipping", entityId);
            }
        } else {
            // Race-8c: the KC user does not exist yet. The REQUEST side does NOT buffer --
            // the buffer is fed solely by the path field of the X (entity-broadcast) message
            // via KcEntityBroadcastConsumer, which every kcMaster instance receives (a URQ
            // request lands on only one pod, so it cannot be the buffer source under the
            // redundant multi-instance setup). This request simply skips; the keySmith CREATE
            // URQ's createUser will consume the path the X message already parked.
            devLog.debug("KC | MOVE | entityId='{}' : no KC user yet, request skipped (path buffered from X message)", entityId);
        }
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

    private void assignRealmRoles(RealmResource realmResource, String kcId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = new ArrayList<>();
        for (RoleRepresentation role : realmResource.roles().list()) {
            if (roleNames.contains(role.getName())) {
                rolesToAssign.add(role);
            }
        }
        if (!rolesToAssign.isEmpty()) {
            realmResource.users().get(kcId).roles().realmLevel().add(rolesToAssign);
            devLog.debug("Assigned roles {} to user: {}", roleNames, kcId);
        }
    }

    private void updateRealmRoles(RealmResource realmResource, String kcId, List<String> roleNames) {
        UserResource userResource = realmResource.users().get(kcId);
        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();

        ArrayList<String> toAdd    = new ArrayList<>(roleNames);
        ArrayList<RoleRepresentation> toRemove = new ArrayList<>();
        for (RoleRepresentation role : currentRoles) {
            if (!toAdd.remove(role.getName())) {
                toRemove.add(role);
            }
        }

        if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
            if (!toRemove.isEmpty()) {
                userResource.roles().realmLevel().remove(toRemove);
            }
            if (!toAdd.isEmpty()) {
                assignRealmRoles(realmResource, kcId, toAdd);
            }
            devLog.debug("Updated roles for user: {}", kcId);
        } else {
            devLog.debug("Realm roles unchanged for user: {}", kcId);
        }
    }
}
