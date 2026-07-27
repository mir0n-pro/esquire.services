package pro.mir0n.esquire.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakRoleConverterTest {

    @Mock
    private Jwt jwt;

    private KeycloakRoleConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakRoleConverter();
    }

    @Test
    void convert_withValidRoles_returnsGrantedAuthorities() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin", "user"));
        when(jwt.getClaims()).thenReturn(Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS, realmAccess));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_user");
    }

    @Test
    void convert_nullRealmAccess_returnsEmptyList() {
        when(jwt.getClaims()).thenReturn(Map.of());

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).isEmpty();
    }

    @Test
    void convert_emptyRealmAccess_returnsEmptyList() {
        when(jwt.getClaims()).thenReturn(Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.of()));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).isEmpty();
    }

    @Test
    void convert_realmAccessWithoutRoles_returnsEmptyList() {
        Map<String, Object> realmAccess = Map.of("other", "value");
        when(jwt.getClaims()).thenReturn(Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS, realmAccess));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).isEmpty();
    }
}
