package com.example.manufacturing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import static com.example.manufacturing.security.Roles.ADMIN;
import static com.example.manufacturing.security.Roles.PLANNER;
import static com.example.manufacturing.security.Roles.SUPERVISOR;
import static com.example.manufacturing.security.Roles.VIEWER;

/**
 * Stateless API security: every /api request needs a JWT access token from Keycloak, and the
 * user's roles decide what they may do. Health probes and API docs stay public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** No token needed: Kubernetes probes, Prometheus scraping and the API documentation. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ProblemDetailAuthErrorHandler authErrors) throws Exception {
        http
                // Bearer tokens, no cookies or sessions, so CSRF protection is not needed.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Everyone with a role may read.
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(VIEWER, PLANNER, SUPERVISOR, ADMIN)
                        // Master data: only admins.
                        .requestMatchers("/api/v1/plants/**", "/api/v1/vehicle-models/**").hasRole(ADMIN)
                        // Shop floor: supervisors report progress.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/production-orders/*/progress")
                                .hasAnyRole(SUPERVISOR, ADMIN)
                        // Start / hold / complete / cancel: planners and supervisors.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/production-orders/*/status")
                                .hasAnyRole(PLANNER, SUPERVISOR, ADMIN)
                        // Create, re-plan and delete orders: planners.
                        .requestMatchers("/api/v1/production-orders/**").hasAnyRole(PLANNER, ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authErrors)
                        .accessDeniedHandler(authErrors))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authErrors)
                        .accessDeniedHandler(authErrors));
        return http.build();
    }

    @Bean
    public ProblemDetailAuthErrorHandler problemDetailAuthErrorHandler(ObjectMapper objectMapper) {
        return new ProblemDetailAuthErrorHandler(objectMapper);
    }

    /** Uses the Keycloak username as the principal name and maps realm roles to authorities. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
