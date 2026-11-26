/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith IKeycloakIdentityService
 * 04/06/2026 mir0n  updateEntityPath() added
 * 08/26/2026 mir0n  updateAccess drops the password and enabled parameters; forcePasswordChange becomes a
 *                   Boolean and removeTotp joins it, so each required action can be withdrawn as well as set
 * 11/23/2026 mir0n  v2.0.3 -- createUser() and updateUserAuthState() lose realmRoles; updateEntityPath() out
 * 11/25/2026 mir0n  v2.0.3 -- createUser() and updateUserAuthState() lose attributes
 */

package pro.mir0n.esquire.kcMaster.service;

public interface IKcIdentityService {

    void createUser(
            String loginId,
            String email,
            String password,
            boolean enabled,
            boolean forcePasswordChange,
            boolean requireTotp,
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
            String correlationId,
            String requestId
    );

    void deleteUser(String loginId, String correlationId, String requestId);
}
