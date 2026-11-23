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
 * 09/05/2026 mir0n  v1.2.15 -- validates the identity claims only: the realm-role check and holdsRealmRole()
 *                   are gone, and the refusal log drops the realmRole field
 * 11/17/2026 mir0n  v2.0.2 -- a token whose azp is on the pure OAuth list needs only sub and preferred_username;
 *                   any other token is checked for the Esquire claims as before
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

import java.util.Set;

/**
 * Requires the Esquire identity claims on a token the gateway has already validated.
 *
 */
public class EsqClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log    = LoggerFactory.getLogger(EsqClaimsValidator.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + EsqClaimsValidator.class.getName());

    private static final OAuth2Error NO_IDENTITY = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The token carries no Esquire identity", null);
    private static final OAuth2Error NO_LOGIN = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The token carries no login id", null);
    private static final String CLAIM_AZP = "azp";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    private final Set<String> pureOAuthClients;

    public EsqClaimsValidator() {
        this(Set.of());
    }

    public EsqClaimsValidator(Set<String> pureOAuthClients) {
        this.pureOAuthClients = pureOAuthClients;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        OAuth2TokenValidatorResult ret;
        String azp = token.getClaimAsString(CLAIM_AZP);
        if (azp != null && pureOAuthClients.contains(azp)) {
            ret = validatePureOAuth(token);
        } else {
            ret = validateClaims(token);
        }
        return ret;
    }

    //xxx: a pure OAuth token carries no Esquire claims; the login id is what the gateway looks them up by
    private OAuth2TokenValidatorResult validatePureOAuth(Jwt token) {
        OAuth2TokenValidatorResult ret;
        String subject = token.getSubject();
        String loginId = token.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        if (isBlank(subject) || isBlank(loginId)) {
            log.warn("EsqClaimsValidator: pure OAuth token refused -- no login id");
            devLog.warn("EsqClaimsValidator: refused -- sub={}, {}={}", !isBlank(subject),
                    CLAIM_PREFERRED_USERNAME, !isBlank(loginId));
            ret = OAuth2TokenValidatorResult.failure(NO_LOGIN);
        } else {
            ret = OAuth2TokenValidatorResult.success();
        }
        return ret;
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt token) {
        OAuth2TokenValidatorResult ret;
        String subject  = token.getSubject();
        String uid      = token.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ID);
        String rootPath = token.getClaimAsString(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH);

        if (isBlank(subject) || isBlank(uid) || isBlank(rootPath)) {
            log.warn("EsqClaimsValidator: token refused -- no Esquire identity");
            // WHICH claim is absent, not the claim set -- the token payload belongs in the develop tier only.
            devLog.warn("EsqClaimsValidator: refused -- sub={}, {}={}, {}={}",
                    !isBlank(subject),
                    EsqConstants.JWT_CLAIM_ENTITY_ID, !isBlank(uid),
                    EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, !isBlank(rootPath));
            ret = OAuth2TokenValidatorResult.failure(NO_IDENTITY);
        } else {
            ret = OAuth2TokenValidatorResult.success();
        }
        return ret;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
