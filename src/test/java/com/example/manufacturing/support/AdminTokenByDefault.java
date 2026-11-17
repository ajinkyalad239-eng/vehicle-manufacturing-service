package com.example.manufacturing.support;

import com.example.manufacturing.security.Roles;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every MockMvc request in a test carry a valid ADMIN token, so functional controller tests
 * focus on behaviour. Access rules themselves are tested in SecurityConfigTest.
 * A request can still override this with its own {@code .with(jwt()...)}.
 */
@TestConfiguration
public class AdminTokenByDefault {

    @Bean
    MockMvcBuilderCustomizer adminTokenOnEveryRequest() {
        return builder -> builder.defaultRequest(get("/")
                .with(jwt().jwt(token -> token.claim("preferred_username", "test-admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + Roles.ADMIN))));
    }
}
