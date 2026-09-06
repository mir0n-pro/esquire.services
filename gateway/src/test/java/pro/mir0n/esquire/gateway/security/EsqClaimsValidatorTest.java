package pro.mir0n.esquire.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway refuses a token that carries no Esquire IDENTITY -- a subject, an entity id and a root path.
 * Roles are not identity: a token naming a real user who holds none is accepted here and refused, if it
 * must be, by the route rules, which answer 403 rather than 401.
 */
class EsqClaimsValidatorTest {

    private final EsqClaimsValidator validator = new EsqClaimsValidator();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("kc-subject")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ID, "42")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                        Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("TREE")));
    }

    @Test
    @DisplayName("a complete Esquire token passes")
    void complete() {
        OAuth2TokenValidatorResult ret = validator.validate(token().build());
        assertThat(ret.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("no esq_uid is refused")
    void noUid() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                        Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("TREE")))
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("no esq_rootpath is refused -- the scope of every read")
    void noRootPath() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ID, "42")
                .claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                        Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("TREE")))
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("a blank esq_rootpath is refused, not treated as a root")
    void blankRootPath() {
        Jwt jwt = token().claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "  ").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("no realm_access at all still passes -- the identity is whole, the rights are the route's question")
    void noRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ID, "42")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("an empty role list still passes, so the refusal can be a 403 that names the account")
    void emptyRoles() {
        Jwt jwt = token().claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of())).build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("the failure names invalid_token, so the gateway answers 401")
    void errorCode() {
        Jwt jwt = token().claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "").build();
        OAuth2TokenValidatorResult ret = validator.validate(jwt);
        assertThat(ret.getErrors()).isNotEmpty();
        assertThat(ret.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }
}
