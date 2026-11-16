package com.example.manufacturing.productionorder.dto;

import com.example.manufacturing.productionorder.ProductionOrderStatus;
import jakarta.validation.constraints.NotNull;

/** Body for PATCH /api/v1/production-orders/{id}/status. */
public record StatusUpdateRequest(@NotNull ProductionOrderStatus status) {
}
