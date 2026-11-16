package com.example.manufacturing.productionorder.dto;

import com.example.manufacturing.productionorder.OrderPriority;
import com.example.manufacturing.productionorder.ProductionOrder;
import com.example.manufacturing.productionorder.ProductionOrderStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ProductionOrderResponse(
        Long id,
        String orderNumber,
        Long plantId,
        String plantCode,
        Long vehicleModelId,
        String modelCode,
        String modelName,
        Integer quantity,
        Integer completedQuantity,
        int progressPercent,
        ProductionOrderStatus status,
        OrderPriority priority,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        Instant actualStartAt,
        Instant actualEndAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductionOrderResponse from(ProductionOrder order) {
        int progress = (int) Math.floor(order.getCompletedQuantity() * 100.0 / order.getQuantity());
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getPlant().getId(),
                order.getPlant().getCode(),
                order.getVehicleModel().getId(),
                order.getVehicleModel().getModelCode(),
                order.getVehicleModel().getName(),
                order.getQuantity(),
                order.getCompletedQuantity(),
                progress,
                order.getStatus(),
                order.getPriority(),
                order.getPlannedStartDate(),
                order.getPlannedEndDate(),
                order.getActualStartAt(),
                order.getActualEndAt(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
