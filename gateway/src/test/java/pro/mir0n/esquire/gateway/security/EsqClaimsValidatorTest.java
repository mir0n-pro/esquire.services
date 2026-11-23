package pro.mir0n.esquire.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway refuses a token that carries no login id -- the key it looks the Esquire claims up by. The
 * token carries no Esquire claims; a token that does is not refused for them, they are just not read.
 */
class EsqClaimsValidatorTest {

    private final EsqClaimsValidator validator = new EsqClaimsValidator();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim("azp", "esq-angular").claim("preferred_username", "mainadmin");
    }

    @Test
    @DisplayName("a token with a subject and a login id passes")
    void loginOnly_passes() {
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("no login id is refused")
    void noLogin_refused() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim("azp", "esq-angular").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("a blank login id is refused")
    void blankLogin_refused() {
        Jwt jwt = token().claim("preferred_username", "  ").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("no subject is refused")
    void noSubject_refused() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("azp", "esq-angular").claim("preferred_username", "mainadmin").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("the failure names invalid_token, so the gateway answers 401")
    void errorCode() {
        Jwt jwt = token().claim("preferred_username", "").build();
        OAuth2TokenValidatorResult ret = validator.validate(jwt);
        assertThat(ret.getErrors()).isNotEmpty();
        assertThat(ret.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }
}
