package com.example.manufacturing.security;

import com.example.manufacturing.plant.PlantService;
import com.example.manufacturing.productionorder.ProductionOrderService;
import com.example.manufacturing.vehiclemodel.VehicleModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the access rules for every role against every kind of endpoint, plus the 401/403 responses.
 * Services are mocks: "allowed" only means security let the request through (not 401/403).
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlantService plantService;

    @MockitoBean
    private VehicleModelService vehicleModelService;

    @MockitoBean
    private ProductionOrderService productionOrderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest(name = "{2} {0} {1} -> allowed={3}")
    @CsvSource({
            // Reading: every role
            "GET,    /api/v1/plants,                         VIEWER,     true",
            "GET,    /api/v1/production-orders,              SUPERVISOR, true",
            // Plants and models: admin only
            "POST,   /api/v1/plants,                         ADMIN,      true",
            "POST,   /api/v1/plants,                         VIEWER,     false",
            "POST,   /api/v1/plants,                         PLANNER,    false",
            "PUT,    /api/v1/plants/1,                       SUPERVISOR, false",
            "DELETE, /api/v1/vehicle-models/1,               ADMIN,      true",
            "DELETE, /api/v1/vehicle-models/1,               PLANNER,    false",
            // Create / re-plan / delete orders: planner + admin
            "POST,   /api/v1/production-orders,              PLANNER,    true",
            "POST,   /api/v1/production-orders,              ADMIN,      true",
            "POST,   /api/v1/production-orders,              SUPERVISOR, false",
            "POST,   /api/v1/production-orders,              VIEWER,     false",
            "PUT,    /api/v1/production-orders/1,            PLANNER,    true",
            "DELETE, /api/v1/production-orders/1,            PLANNER,    true",
            "DELETE, /api/v1/production-orders/1,            SUPERVISOR, false",
            // Status changes: planner + supervisor + admin
            "PATCH,  /api/v1/production-orders/1/status,     PLANNER,    true",
            "PATCH,  /api/v1/production-orders/1/status,     SUPERVISOR, true",
            "PATCH,  /api/v1/production-orders/1/status,     VIEWER,     false",
            // Progress reporting: supervisor + admin
            "PATCH,  /api/v1/production-orders/1/progress,   SUPERVISOR, true",
            "PATCH,  /api/v1/production-orders/1/progress,   ADMIN,      true",
            "PATCH,  /api/v1/production-orders/1/progress,   PLANNER,    false",
            // Anything else just needs a valid token
            "POST,   /internal/anything,                     VIEWER,     true"
    })
    void accessMatrix(String method, String path, String role, boolean allowed) throws Exception {
        int status = mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andReturn().getResponse().getStatus();

        if (allowed) {
            assertThat(status).as("status for %s %s as %s", method, path, role).isNotIn(401, 403);
        } else {
            assertThat(status).as("status for %s %s as %s", method, path, role).isEqualTo(403);
        }
    }

    @Test
    void noToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/plants"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/v1/plants"));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("bad signature"));

        mvc.perform(get("/api/v1/plants").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void wrongRole_returns403ProblemDetail() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/v1/plants")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action."));
    }

    @Test
    void tokenWithoutAnyRole_cannotRead() throws Exception {
        mvc.perform(get("/api/v1/plants").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpoints_needNoToken() throws Exception {
        // These endpoints are not part of this web slice (404), but security must not block them (no 401).
        for (String path : new String[] {"/actuator/health/readiness", "/v3/api-docs", "/swagger-ui.html"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isNotEqualTo(401);
        }
    }

    @Test
    void viewer_readsData() throws Exception {
        when(plantService.findAll(any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/plants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }
}
