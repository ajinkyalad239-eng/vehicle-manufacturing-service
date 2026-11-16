package com.example.manufacturing.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void describesTheApi() {
        OpenAPI openApi = new OpenApiConfig().manufacturingOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Vehicle Manufacturing Service API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    }
}
