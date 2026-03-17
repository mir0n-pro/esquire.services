/*
 *  Esquire frameworks (tm)
 *  keySmith service - Keycloak Identity Service Implementation
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/11/2026 mir0n Initial creation
 * 03/16/2026 mir0n  updateUserAuthState(): removeTotp parameter added; OTP credential removal block added
 *                   deleteUser() added
 */

package pro.mir0n.esquire.keySmith.service.impl;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.LoggerFactory;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import pro.mir0n.esquire.keySmith.config.KeycloakConfig;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.keySmith.service.IKeycloakIdentityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakIdentityService implements IKeycloakIdentityService {
    private static final org.slf4j.Logger kcAudit = LoggerFactory.getLogger("kc.audit");

    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;

    @Async
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
        try {
            kcAudit.info("KC | CREATE | username={} | state=STARTED | cid={} | rid={}", loginId, correlationId, requestId);
            log.debug("Creating user in Keycloak: username={}, email={}", loginId, email);

            RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
            UsersResource usersResource = realmResource.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(loginId);
            user.setEmail(email);
            user.setEnabled(enabled);
            user.setEmailVerified(false);

            // Configure required actions
            if (forcePasswordChange) {
                user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
            }

            // Configure TOTP
            if (requireTotp) {
                List<String> requiredActions = user.getRequiredActions() != null
                    ? user.getRequiredActions()
                    : Collections.emptyList();
                requiredActions = new java.util.ArrayList<>(requiredActions);
                requiredActions.add("CONFIGURE_TOTP");
                user.setRequiredActions(requiredActions);
            }

            // Set custom attributes
            if (attributes != null && !attributes.isEmpty()) {
                user.setAttributes(attributes);
            }

            // Create user
            Response response = usersResource.create(user);

            if (response.getStatus() != 201) {
                String errorMsg = "Failed to create user in Keycloak: " + response.getStatusInfo();
                throw new RuntimeException(errorMsg);
            }

            String kcId = extractUserIdFromResponse(response);

            // Set password
            if (password != null && !password.isEmpty()) {
                setUserPassword(usersResource, kcId, password, !forcePasswordChange);
            }

            // Assign realm roles
            if (realmRoles != null && !realmRoles.isEmpty()) {
                assignRealmRoles(realmResource, kcId, realmRoles);
            }

            response.close();
            kcAudit.info("KC | CREATE | username={} | state=SUCCESS | kcUserId={} | cid={} | rid={}", loginId, kcId, correlationId, requestId);
        } catch (Throwable e) {
            kcAudit.error("KC | CREATE | username={} | state=FAILURE | error={} | cid={} | rid={}", loginId, e.getMessage(), correlationId, requestId);
            kcAudit.error("KC | CREATE | FAILURE |", e);
            //throw e;
        }
    }

    @Async
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
try {
    kcAudit.info("KC | UPDATE| username={} | state=STARTED | cid={} | rid={}", loginId, correlationId, requestId);
    log.debug("Updating user auth state in Keycloak: username={}", loginId);

    kcAudit.debug("KC | UPDATE | username={} | 1 | newLoginId={} | email={} forcePasswordChange={} requireTotp={} realmRoles={} attributes={}",
            loginId, newLoginId, email, forcePasswordChange, requireTotp, realmRoles, attributes);

    RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
    kcAudit.debug("KC | UPDATE | username={} | 2 |");
    UsersResource usersResource = realmResource.users();
    kcAudit.debug("KC | UPDATE | usersResource={} | 3 |", usersResource);

    // Find user by username
    List<UserRepresentation> users = usersResource.search(loginId, true);
    kcAudit.debug("KC | UPDATE | users={} |4 |", users);
    if (users.isEmpty()) {
        throw new NotFoundException("User not found: " + loginId);
    }

    String kcId = users.get(0).getId();
    kcAudit.debug("KC | UPDATE | kcId={} |5 |", kcId);
    UserResource userResource = usersResource.get(kcId);
    kcAudit.debug("KC | UPDATE | userResource={} |6 |", userResource);
    // Full representation is required: the search result carries minimal data (attributes = null).
    // Using toRepresentation() ensures existing KC attributes (esq_uid, esq_rootpath, etc.)
    // are preserved when we merge the incoming attribute updates.
    UserRepresentation user = userResource.toRepresentation();
    kcAudit.debug("KC | UPDATE | user={} |7 |", user);

    // Update email if provided
    if (email != null) {
        user.setEmail(email);
    }
    kcAudit.debug("KC | UPDATE | user={} |8 |", user);

    // Update enabled status if provided
//        if (enabled != null) {
//            user.setEnabled(enabled);
//        }

        // Update required actions
        List<String> requiredActions = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(forcePasswordChange)) {
            requiredActions.add("UPDATE_PASSWORD");
        }
        if (Boolean.TRUE.equals(requireTotp)) {
            requiredActions.add("CONFIGURE_TOTP");
        }
        if (!requiredActions.isEmpty()) {
            user.setRequiredActions(requiredActions);
        //} else if (forcePasswordChange != null || requireTotp != null) {
            // If explicitly set to false, clear required actions
       //     user.setRequiredActions(Collections.emptyList());
            //
        }

    // Merge loginId + any extra attributes into existing KC attributes
    // (never replace the whole map — preserves esq_uid, esq_rootpath, etc.)
    java.util.Map<String, List<String>> merged = new java.util.HashMap<>();
    if (user.getAttributes() != null) {
        merged.putAll(user.getAttributes());
    }

    if (newLoginId != null && !newLoginId.equals(loginId)) {
        user.setUsername(newLoginId);
        kcAudit.debug("KC | UPDATE | user={} |9 |", user);
    }

    if (attributes != null) {
        merged.putAll(attributes);
    }
    user.setAttributes(merged);
    kcAudit.debug("KC | UPDATE | user={} |A |", user);

    // Update user
    userResource.update(user);
    kcAudit.debug("KC | UPDATE | user={} |B |", user);
    log.debug("User updated: {}", loginId);

    // Update password if provided
    //if (password != null && !password.isEmpty()) {
    //    setUserPassword(usersResource, kcId, password, !Boolean.TRUE.equals(forcePasswordChange));
    //}

    // Update realm roles if provided
    if (realmRoles != null) {
        updateRealmRoles(realmResource, kcId, realmRoles);
        kcAudit.debug("KC | UPDATE | user={} |C |", user);
    }

    // Remove OTP credentials if requested
    if (Boolean.TRUE.equals(removeTotp)) {
        userResource.credentials().stream()
            .filter(c -> "otp".equals(c.getType()))
            .forEach(c -> userResource.removeCredential(c.getId()));
        kcAudit.info("KC | UPDATE | username={} | OTP credentials removed | cid={} | rid={}", loginId, correlationId, requestId);
    }

    kcAudit.info("KC | SUCCESS | username={} | state=SUCCESS | cid={} | rid={}", loginId, correlationId, requestId);
}catch (Throwable e) {
    kcAudit.error("KC | FAILURE | username={} | state=FAILURE | error={} | cid={} | rid={}", loginId, e.getMessage(), correlationId, requestId);
    kcAudit.error("KC | FAILURE |",e);
    //throw e;
}
    }

    @Async
    @Override
    public void deleteUser(String loginId, String correlationId, String requestId) {
        try {
            kcAudit.info("KC | DELETE | username={} | state=STARTED | cid={} | rid={}", loginId, correlationId, requestId);
            log.debug("Deleting user from Keycloak: username={}", loginId);

            RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
            UsersResource usersResource = realmResource.users();

            // Find user by username
            List<UserRepresentation> users = usersResource.search(loginId, true);
            if (users.isEmpty()) {
                throw new NotFoundException("User not found: " + loginId);
            }

            String kcId = users.get(0).getId();
            usersResource.delete(kcId);

            kcAudit.info("KC | DELETE | username={} | state=SUCCESS | cid={} | rid={}", loginId, correlationId, requestId);
            log.debug("User deleted: {}", kcId);
        } catch (Throwable e) {
            kcAudit.error("KC | DELETE | username={} | state=FAILURE | error={} | cid={} | rid={}", loginId, e.getMessage(), correlationId, requestId);
            kcAudit.error("KC | DELETE | FAILURE |", e);
            //throw e;
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

        UserResource userResource = usersResource.get(kcId);
        userResource.resetPassword(credential);

        kcAudit.debug("Password set for user: {}", kcId);
    }

    private void assignRealmRoles(RealmResource realmResource, String kcId, List<String> roleNames) {
        var availableRoles = realmResource.roles().list();
        var rolesToAssign = availableRoles.stream()
                .filter(role -> roleNames.contains(role.getName()))
                .collect(Collectors.toList());

        if (!rolesToAssign.isEmpty()) {
            realmResource.users().get(kcId).roles().realmLevel().add(rolesToAssign);
            kcAudit.debug("Assigned roles {} to user: {}", roleNames, kcId);
        }
    }

    private void updateRealmRoles(RealmResource realmResource, String kcId, List<String> roleNames) {
        UserResource userResource = realmResource.users().get(kcId);

        var currentRoles = userResource.roles().realmLevel().listAll();

        // toAdd starts as all given names; toRemove collects current roles not in given.
        // One loop: if a current role is found in toAdd it's already assigned (remove from toAdd = no-op);
        // if not found it must be removed.
        var toAdd    = new ArrayList<>(roleNames);
        var toRemove = new ArrayList<RoleRepresentation>();
        for (var role : currentRoles) {
            if (!toAdd.remove(role.getName())) {
                toRemove.add(role);
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            kcAudit.debug("Realm roles unchanged for user: {}", kcId);
            return;
        }

        if (!toRemove.isEmpty()) {
            userResource.roles().realmLevel().remove(toRemove);
            kcAudit.debug("Removed roles {} from user: {}", toRemove.stream().map(r -> r.getName()).collect(Collectors.toList()), kcId);
        }
        if (!toAdd.isEmpty()) {
            assignRealmRoles(realmResource, kcId, toAdd);
        }

        kcAudit.debug("Updated roles for user: {}", kcId);
    }
}
