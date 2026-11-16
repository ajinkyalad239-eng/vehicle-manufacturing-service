package com.example.manufacturing.vehiclemodel;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelRequest;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleModelController.class)
class VehicleModelControllerTest {

    private static final String BASE = "/api/v1/vehicle-models";

    private static final String VALID_BODY = """
            {"modelCode":"AUR-EV","name":"Aurora EV","category":"CROSSOVER","powertrain":"ELECTRIC",
             "launchYear":2025,"basePrice":4250000.00}
            """;

    private static final VehicleModelResponse MODEL = new VehicleModelResponse(10L, "AUR-EV", "Aurora EV",
            VehicleCategory.CROSSOVER, Powertrain.ELECTRIC, 2025, new BigDecimal("4250000.00"), true,
            Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-16T00:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VehicleModelService vehicleModelService;

    @Test
    void list_withoutFilters_returnsPage() throws Exception {
        when(vehicleModelService.findAll(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(MODEL)));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].modelCode").value("AUR-EV"))
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void list_withFilters_passesThemToService() throws Exception {
        when(vehicleModelService.findAll(eq(Powertrain.ELECTRIC), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(MODEL)));

        mvc.perform(get(BASE).param("powertrain", "ELECTRIC").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].powertrain").value("ELECTRIC"));
    }

    @Test
    void get_existing_returns200() throws Exception {
        when(vehicleModelService.findById(10L)).thenReturn(MODEL);

        mvc.perform(get(BASE + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CROSSOVER"))
                .andExpect(jsonPath("$.launchYear").value(2025));
    }

    @Test
    void get_missing_returns404() throws Exception {
        when(vehicleModelService.findById(99L)).thenThrow(new ResourceNotFoundException("Vehicle model", 99L));

        mvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_valid_returns201() throws Exception {
        when(vehicleModelService.create(any(VehicleModelRequest.class))).thenReturn(MODEL);

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/vehicle-models/10")));
    }

    @Test
    void create_invalid_returns400WithFieldErrors() throws Exception {
        String body = """
                {"modelCode":"AUR-EV","name":"Aurora","category":null,"powertrain":"ELECTRIC",
                 "launchYear":1800,"basePrice":-1}
                """;

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.launchYear").exists())
                .andExpect(jsonPath("$.errors.basePrice").exists());
    }

    @Test
    void create_unknownEnumValue_returns400() throws Exception {
        String body = VALID_BODY.replace("ELECTRIC", "STEAM");

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_valid_returns200() throws Exception {
        when(vehicleModelService.update(eq(10L), any(VehicleModelRequest.class))).thenReturn(MODEL);

        mvc.perform(put(BASE + "/10").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aurora EV"));
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete(BASE + "/10"))
                .andExpect(status().isNoContent());

        verify(vehicleModelService).delete(10L);
    }

    @Test
    void delete_withOrders_returns422() throws Exception {
        doThrow(new BusinessRuleException("has production orders")).when(vehicleModelService).delete(10L);

        mvc.perform(delete(BASE + "/10"))
                .andExpect(status().isUnprocessableEntity());
    }
}
