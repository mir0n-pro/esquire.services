/*
 *  Esquire frameworks (tm)
 *  keySmith service - Keycloak Identity Service Interface
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/11/2026 mir0n Initial creation
 * 03/16/2026 mir0n  updateUserAuthState(): removeTotp parameter added
 *                   deleteUser() added
 */

package pro.mir0n.esquire.keySmith.service;

import java.util.List;
import java.util.Map;

public interface IKeycloakIdentityService {

    void createUser(
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
    );

    void updateUserAuthState(
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
    );

    void deleteUser(String loginId, String correlationId, String requestId);
}
