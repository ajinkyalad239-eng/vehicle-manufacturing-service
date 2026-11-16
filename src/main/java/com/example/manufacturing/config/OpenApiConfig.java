package com.example.manufacturing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI manufacturingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Vehicle Manufacturing Service API")
                .version("v1")
                .description("Manage assembly plants, vehicle models and production orders."));
    }
}
