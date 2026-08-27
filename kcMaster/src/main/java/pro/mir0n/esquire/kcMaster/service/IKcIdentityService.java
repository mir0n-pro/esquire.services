/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith IKeycloakIdentityService
 * 04/06/2026 mir0n  updateEntityPath() added
 * 08/26/2026 mir0n  updateAccess drops the password and enabled parameters; forcePasswordChange becomes a
 *                   Boolean and removeTotp joins it, so each required action can be withdrawn as well as set
 */

package pro.mir0n.esquire.kcMaster.service;

import java.util.List;
import java.util.Map;

public interface IKcIdentityService {

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
            Boolean forcePasswordChange,
            Boolean requireTotp,
            Boolean removeTotp,
            List<String> realmRoles,
            Map<String, List<String>> attributes,
            String correlationId,
            String requestId
    );

    void deleteUser(String loginId, String correlationId, String requestId);

    void updateEntityPath(String entityId, String newPath, String correlationId, String requestId);
}
