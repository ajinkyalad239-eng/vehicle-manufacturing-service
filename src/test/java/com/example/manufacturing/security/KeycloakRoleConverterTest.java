package com.example.manufacturing.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private static Jwt jwt(Consumer<Map<String, Object>> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims)
                .build();
    }

    private List<String> authorities(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test
    void readsKeycloakRealmRoles() {
        Jwt token = jwt(c -> c.put("realm_access", Map.of("roles", List.of("PLANNER", "offline_access"))));

        assertThat(authorities(token)).containsExactly("ROLE_OFFLINE_ACCESS", "ROLE_PLANNER");
    }

    @Test
    void readsPlainRolesClaimAndUpperCases() {
        Jwt token = jwt(c -> c.put("roles", List.of("viewer")));

        assertThat(authorities(token)).containsExactly("ROLE_VIEWER");
    }

    @Test
    void combinesBothClaimsWithoutDuplicates() {
        Jwt token = jwt(c -> {
            c.put("realm_access", Map.of("roles", List.of("ADMIN")));
            c.put("roles", List.of("ADMIN", "SUPERVISOR"));
        });

        assertThat(authorities(token)).containsExactly("ROLE_ADMIN", "ROLE_SUPERVISOR");
    }

    @Test
    void realmAccessWithoutRoles_givesNoAuthorities() {
        Jwt token = jwt(c -> c.put("realm_access", Map.of("other", "value")));

        assertThat(authorities(token)).isEmpty();
    }

    @Test
    void noRoleClaims_givesNoAuthorities() {
        assertThat(authorities(jwt(c -> c.put("scope", "openid")))).isEmpty();
    }

    @Test
    void authenticationUsesKeycloakUsernameAsName() {
        Jwt token = jwt(c -> {
            c.put("preferred_username", "planner");
            c.put("realm_access", Map.of("roles", List.of("PLANNER")));
        });

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) SecurityConfig.jwtAuthenticationConverter().convert(token);

        assertThat(authentication.getName()).isEqualTo("planner");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PLANNER");
    }
}
