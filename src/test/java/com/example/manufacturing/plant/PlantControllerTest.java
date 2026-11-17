package com.example.manufacturing.plant;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.DuplicateResourceException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.plant.dto.PlantRequest;
import com.example.manufacturing.plant.dto.PlantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.manufacturing.security.SecurityConfig;
import com.example.manufacturing.support.AdminTokenByDefault;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
@Import({SecurityConfig.class, AdminTokenByDefault.class})
class PlantControllerTest {

    private static final String BASE = "/api/v1/plants";

    private static final String VALID_BODY = """
            {"code":"JP-KNT-01","name":"Kanto Assembly Plant","city":"Yokohama",
             "country":"Japan","dailyCapacity":1200,"status":"ACTIVE"}
            """;

    private static final PlantResponse PLANT = new PlantResponse(1L, "JP-KNT-01", "Kanto Assembly Plant",
            "Yokohama", "Japan", 1200, PlantStatus.ACTIVE,
            Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-16T00:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlantService plantService;

    /** Never called: requests carry a ready-made test token (see AdminTokenByDefault). */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void list_returnsPageAndCorrelationId() throws Exception {
        when(plantService.findAll(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PLANT), PageRequest.of(0, 20), 1));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.content[0].code").value("JP-KNT-01"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-16T00:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void list_withStatusFilter_passesItToService() throws Exception {
        when(plantService.findAll(eq(PlantStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PLANT)));

        mvc.perform(get(BASE).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void list_withUnknownStatus_returns400() throws Exception {
        mvc.perform(get(BASE).param("status", "BROKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Invalid value 'BROKEN' for parameter 'status'"));
    }

    @Test
    void get_existing_returns200() throws Exception {
        when(plantService.findById(1L)).thenReturn(PLANT);

        mvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.dailyCapacity").value(1200));
    }

    @Test
    void get_missing_returns404ProblemDetail() throws Exception {
        when(plantService.findById(99L)).thenThrow(new ResourceNotFoundException("Plant", 99L));

        mvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Plant with id 99 was not found"));
    }

    @Test
    void create_valid_returns201WithLocation() throws Exception {
        when(plantService.create(any(PlantRequest.class))).thenReturn(PLANT);

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/plants/1")))
                .andExpect(jsonPath("$.code").value("JP-KNT-01"));
    }

    @Test
    void create_invalid_returns400WithFieldErrors() throws Exception {
        String body = """
                {"code":"bad code","name":"","city":"Yokohama","country":"Japan","dailyCapacity":0}
                """;

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.dailyCapacity").exists());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(plantService.create(any(PlantRequest.class)))
                .thenThrow(new DuplicateResourceException("Plant with code 'JP-KNT-01' already exists"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate resource"));
    }

    @Test
    void update_valid_returns200() throws Exception {
        when(plantService.update(eq(1L), any(PlantRequest.class))).thenReturn(PLANT);

        mvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kanto Assembly Plant"));
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent());

        verify(plantService).delete(1L);
    }

    @Test
    void delete_withOrders_returns422() throws Exception {
        doThrow(new BusinessRuleException("Plant 'JP-KNT-01' has production orders"))
                .when(plantService).delete(1L);

        mvc.perform(delete(BASE + "/1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }
}
