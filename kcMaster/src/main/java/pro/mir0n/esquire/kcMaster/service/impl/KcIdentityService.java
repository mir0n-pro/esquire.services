/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith KeycloakIdentityService
 *                   @Async removed — KC calls are synchronous here so handler can publish URS after completion
 * 03/21/2026 mir0n  three-tier logging: kcAudit→devLog; KC state events (STARTED/SUCCESS) to log.info;
 *                   all log.debug→devLog.debug; unused imports removed
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
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;
import pro.mir0n.esquire.kcMaster.service.IKcIdentityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KcIdentityService implements IKcIdentityService {
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcIdentityService.class.getName());

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;

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
        log.info("KC | CREATE | username={} | state=SUCCESS | kcUserId={}", loginId, kcId);
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

        if (email != null) {
            user.setEmail(email);
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
        }

        // Merge attributes — never replace whole map, preserves esq_uid, esq_rootpath, etc.
        java.util.Map<String, List<String>> merged = new java.util.HashMap<>();
        if (user.getAttributes() != null) {
            merged.putAll(user.getAttributes());
        }

        if (newLoginId != null && !newLoginId.equals(loginId)) {
            user.setUsername(newLoginId);
        }

        if (attributes != null) {
            merged.putAll(attributes);
        }
        user.setAttributes(merged);

        userResource.update(user);
        devLog.debug("User updated: {}", loginId);

        if (realmRoles != null) {
            updateRealmRoles(realmResource, kcId, realmRoles);
        }

        if (Boolean.TRUE.equals(removeTotp)) {
            userResource.credentials().stream()
                    .filter(c -> "otp".equals(c.getType()))
                    .forEach(c -> userResource.removeCredential(c.getId()));
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
        var rolesToAssign = realmResource.roles().list().stream()
                .filter(role -> roleNames.contains(role.getName()))
                .collect(Collectors.toList());
        if (!rolesToAssign.isEmpty()) {
            realmResource.users().get(kcId).roles().realmLevel().add(rolesToAssign);
            devLog.debug("Assigned roles {} to user: {}", roleNames, kcId);
        }
    }

    private void updateRealmRoles(RealmResource realmResource, String kcId, List<String> roleNames) {
        UserResource userResource = realmResource.users().get(kcId);
        var currentRoles = userResource.roles().realmLevel().listAll();

        var toAdd    = new ArrayList<>(roleNames);
        var toRemove = new ArrayList<RoleRepresentation>();
        for (var role : currentRoles) {
            if (!toAdd.remove(role.getName())) {
                toRemove.add(role);
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            devLog.debug("Realm roles unchanged for user: {}", kcId);
            return;
        }
        if (!toRemove.isEmpty()) {
            userResource.roles().realmLevel().remove(toRemove);
        }
        if (!toAdd.isEmpty()) {
            assignRealmRoles(realmResource, kcId, toAdd);
        }
        devLog.debug("Updated roles for user: {}", kcId);
    }
}
