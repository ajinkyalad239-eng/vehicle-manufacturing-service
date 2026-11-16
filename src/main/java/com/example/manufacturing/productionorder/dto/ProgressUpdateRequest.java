package com.example.manufacturing.productionorder.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body for PATCH /api/v1/production-orders/{id}/progress: total vehicles built so far. */
public record ProgressUpdateRequest(@NotNull @PositiveOrZero Integer completedQuantity) {
}
