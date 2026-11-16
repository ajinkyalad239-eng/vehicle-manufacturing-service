package com.example.manufacturing.productionorder.dto;

import com.example.manufacturing.productionorder.OrderPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for POST /api/v1/production-orders and PUT /api/v1/production-orders/{id}. */
public record ProductionOrderRequest(

        @NotNull
        Long plantId,

        @NotNull
        Long vehicleModelId,

        @NotNull @Positive @Max(100_000)
        Integer quantity,

        /** Optional; defaults to MEDIUM. */
        OrderPriority priority,

        @NotNull
        LocalDate plannedStartDate,

        @NotNull
        LocalDate plannedEndDate,

        @Size(max = 500)
        String notes) {
}
