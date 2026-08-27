/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/24/2026 mir0n  created: the gateway refuses a token that carries no Esquire identity -- the same
 *                   four claims the services' JwtAuthenticationFilter demands, checked at the door
 */
package pro.mir0n.esquire.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;
import java.util.Map;

/**
 * Requires the Esquire identity claims on a token the gateway has already validated.
 *
 */
public class EsqClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log    = LoggerFactory.getLogger(EsqClaimsValidator.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqClaimsValidator.class.getName());

    private static final OAuth2Error NO_IDENTITY = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The token carries no Esquire identity", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        OAuth2TokenValidatorResult ret;
        String subject  = token.getSubject();
        String uid      = token.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ID);
        String rootPath = token.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH);
        boolean roleHeld = holdsRealmRole(token);

        if (isBlank(subject) || isBlank(uid) || isBlank(rootPath) || !roleHeld) {
            log.warn("EsqClaimsValidator: token refused -- no Esquire identity");
            // WHICH claim is absent, not the claim set -- the token payload belongs in the develop tier only.
            devLog.warn("EsqClaimsValidator: refused -- sub={}, {}={}, {}={}, realmRole={}",
                    !isBlank(subject),
                    EsqConstants.JWT_CLAIM_ENTITY_ID, !isBlank(uid),
                    EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, !isBlank(rootPath),
                    roleHeld);
            ret = OAuth2TokenValidatorResult.failure(NO_IDENTITY);
        } else {
            ret = OAuth2TokenValidatorResult.success();
        }
        return ret;
    }

    private static boolean holdsRealmRole(Jwt token) {
        boolean ret = false;
        Map<String, Object> realmAccess = token.getClaimAsMap(EsqConstants.JWT_CLAIM_REALM_ACCESS);
        if (realmAccess != null) {
            Object roles = realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES);
            if (roles instanceof List) {
                ret = !((List) roles).isEmpty();
            }
        }
        return ret;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
