package com.example.manufacturing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.manufacturing.support.AdminTokenByDefault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack against a real PostgreSQL (Docker required): Flyway migrations, Hibernate schema
 * validation, JPQL filters with null parameters, constraints and the complete order life-cycle.
 * Runs in `mvn verify` via the failsafe plugin; skip with -DskipITs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(AdminTokenByDefault.class)
@Testcontainers
class ManufacturingApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createPlant(String code, int capacity) throws Exception {
        String body = """
                {"code":"%s","name":"IT Plant","city":"Tochigi","country":"Japan","dailyCapacity":%d}
                """.formatted(code, capacity);
        MvcResult result = mvc.perform(post("/api/v1/plants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asLong();
    }

    private long createModel(String code) throws Exception {
        String body = """
                {"modelCode":"%s","name":"IT Model","category":"SEDAN","powertrain":"HYBRID",
                 "launchYear":2026,"basePrice":3100000.00}
                """.formatted(code);
        MvcResult result = mvc.perform(post("/api/v1/vehicle-models").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asLong();
    }

    @Test
    void demoDataAndHealthAreAvailable() throws Exception {
        mvc.perform(get("/api/v1/plants").param("status", "MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("US-TN-01"));
        mvc.perform(get("/api/v1/vehicle-models").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].modelCode").value("CITY-VAN"));
        mvc.perform(get("/api/v1/production-orders").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value("PO-SEED-0001"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void fullProductionOrderLifecycle() throws Exception {
        long plantId = createPlant("IT-LIFE-01", 100);
        long modelId = createModel("IT-LIFE-M1");
        LocalDate start = LocalDate.now().plusDays(1);

        String orderBody = """
                {"plantId":%d,"vehicleModelId":%d,"quantity":500,"priority":"HIGH",
                 "plannedStartDate":"%s","plannedEndDate":"%s"}
                """.formatted(plantId, modelId, start, start.plusDays(4));
        MvcResult created = mvc.perform(post("/api/v1/production-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn();
        long orderId = json(created).get("id").asLong();

        // Filtering by plant uses the JPQL query with null status/model parameters.
        mvc.perform(get("/api/v1/production-orders").param("plantId", String.valueOf(plantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Plant and model are protected while an order references them.
        mvc.perform(delete("/api/v1/plants/" + plantId)).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/v1/vehicle-models/" + modelId)).andExpect(status().isUnprocessableEntity());

        mvc.perform(patch("/api/v1/production-orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualStartAt").isNotEmpty());

        mvc.perform(patch("/api/v1/production-orders/" + orderId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedQuantity\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(40));

        MvcResult done = mvc.perform(patch("/api/v1/production-orders/" + orderId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedQuantity\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        assertThat(json(done).get("actualEndAt").isNull()).isFalse();

        // Completed orders are immutable.
        mvc.perform(delete("/api/v1/production-orders/" + orderId)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void plannedOrderCanBeCancelledAndDeleted_thenPlantAndModelCanBeDeleted() throws Exception {
        long plantId = createPlant("IT-DEL-01", 1000);
        long modelId = createModel("IT-DEL-M1");
        LocalDate start = LocalDate.now().plusDays(2);
        String orderBody = """
                {"plantId":%d,"vehicleModelId":%d,"quantity":10,
                 "plannedStartDate":"%s","plannedEndDate":"%s"}
                """.formatted(plantId, modelId, start, start);
        long orderId = json(mvc.perform(post("/api/v1/production-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andReturn()).get("id").asLong();

        mvc.perform(patch("/api/v1/production-orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/production-orders/" + orderId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/plants/" + plantId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/vehicle-models/" + modelId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/plants/" + plantId)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateCodeAndBadSortAreRejected() throws Exception {
        createPlant("IT-DUP-01", 100);
        mvc.perform(post("/api/v1/plants").contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"IT-DUP-01","name":"Dup","city":"Tochigi","country":"Japan","dailyCapacity":100}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/plants").param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort property"));
    }
}
