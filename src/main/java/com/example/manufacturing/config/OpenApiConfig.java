package com.example.manufacturing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI manufacturingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vehicle Manufacturing Service API")
                        .version("v1")
                        .description("Manage assembly plants, vehicle models and production orders. "
                                + "Click Authorize and paste a Keycloak access token."))
                // Adds the "Authorize" button to Swagger UI.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
