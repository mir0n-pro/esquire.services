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
 * The gateway must refuse at the door exactly what JwtClaimsExtractionFilter refuses at the service door:
 * a subject, an entity id, a root path and at least one realm role.
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
    @DisplayName("no realm_access at all is refused")
    void noRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ID, "42")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("an empty role list is refused")
    void emptyRoles() {
        Jwt jwt = token().claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of())).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
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
