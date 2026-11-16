package com.example.manufacturing.productionorder;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.productionorder.dto.ProductionOrderRequest;
import com.example.manufacturing.productionorder.dto.ProductionOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductionOrderController.class)
class ProductionOrderControllerTest {

    private static final String BASE = "/api/v1/production-orders";

    private static final String VALID_BODY = """
            {"plantId":1,"vehicleModelId":10,"quantity":1000,"priority":"HIGH",
             "plannedStartDate":"2026-12-01","plannedEndDate":"2026-12-10","notes":"Launch batch"}
            """;

    private static final ProductionOrderResponse ORDER = new ProductionOrderResponse(5L, "PO-20261116-3F9A1C",
            1L, "JP-KNT-01", 10L, "AUR-EV", "Aurora EV", 1000, 250, 25,
            ProductionOrderStatus.IN_PROGRESS, OrderPriority.HIGH,
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10),
            Instant.parse("2026-12-01T00:00:00Z"), null, "Launch batch",
            Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductionOrderService orderService;

    @Test
    void list_withoutFilters_returnsPage() throws Exception {
        when(orderService.findAll(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ORDER)));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value("PO-20261116-3F9A1C"))
                .andExpect(jsonPath("$.content[0].plannedStartDate").value("2026-12-01"))
                .andExpect(jsonPath("$.content[0].progressPercent").value(25));
    }

    @Test
    void list_withFilters_passesThemToService() throws Exception {
        when(orderService.findAll(eq(ProductionOrderStatus.IN_PROGRESS), eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ORDER)));

        mvc.perform(get(BASE).param("status", "IN_PROGRESS").param("plantId", "1").param("vehicleModelId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].plantCode").value("JP-KNT-01"));
    }

    @Test
    void list_withNonNumericPlantId_returns400() throws Exception {
        mvc.perform(get(BASE).param("plantId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void get_returns200() throws Exception {
        when(orderService.findById(5L)).thenReturn(ORDER);

        mvc.perform(get(BASE + "/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelName").value("Aurora EV"));
    }

    @Test
    void create_valid_returns201() throws Exception {
        when(orderService.create(any(ProductionOrderRequest.class))).thenReturn(ORDER);

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/production-orders/5")));
    }

    @Test
    void create_missingFields_returns400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plantId").exists())
                .andExpect(jsonPath("$.errors.vehicleModelId").exists())
                .andExpect(jsonPath("$.errors.quantity").exists())
                .andExpect(jsonPath("$.errors.plannedStartDate").exists())
                .andExpect(jsonPath("$.errors.plannedEndDate").exists());
    }

    @Test
    void create_capacityExceeded_returns422() throws Exception {
        when(orderService.create(any(ProductionOrderRequest.class)))
                .thenThrow(new BusinessRuleException("need at least 11 days"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("need at least 11 days"));
    }

    @Test
    void update_returns200() throws Exception {
        when(orderService.update(eq(5L), any(ProductionOrderRequest.class))).thenReturn(ORDER);

        mvc.perform(put(BASE + "/5").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(orderService.changeStatus(5L, ProductionOrderStatus.IN_PROGRESS)).thenReturn(ORDER);

        mvc.perform(patch(BASE + "/5/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void changeStatus_missingStatus_returns400() throws Exception {
        mvc.perform(patch(BASE + "/5/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void updateProgress_returns200() throws Exception {
        when(orderService.updateProgress(5L, 250)).thenReturn(ORDER);

        mvc.perform(patch(BASE + "/5/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedQuantity\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedQuantity").value(250));
    }

    @Test
    void updateProgress_negative_returns400() throws Exception {
        mvc.perform(patch(BASE + "/5/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedQuantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.completedQuantity").exists());
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete(BASE + "/5"))
                .andExpect(status().isNoContent());

        verify(orderService).delete(5L);
    }

    @Test
    void delete_startedOrder_returns422() throws Exception {
        doThrow(new BusinessRuleException("cancel it first")).when(orderService).delete(5L);

        mvc.perform(delete(BASE + "/5"))
                .andExpect(status().isUnprocessableEntity());
    }
}
