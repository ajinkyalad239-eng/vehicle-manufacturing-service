package com.example.manufacturing.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Turns the roles inside a JWT into Spring Security authorities ({@code ROLE_PLANNER}, ...).
 * Reads Keycloak's {@code realm_access.roles} claim and, for other identity providers, a plain {@code roles} claim.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new TreeSet<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> keycloakRoles) {
            keycloakRoles.forEach(role -> roles.add(String.valueOf(role)));
        }

        List<String> plainRoles = jwt.getClaimAsStringList("roles");
        if (plainRoles != null) {
            roles.addAll(plainRoles);
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }
}
