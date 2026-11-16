package com.example.manufacturing.vehiclemodel.dto;

import com.example.manufacturing.vehiclemodel.Powertrain;
import com.example.manufacturing.vehiclemodel.VehicleCategory;
import com.example.manufacturing.vehiclemodel.VehicleModel;

import java.math.BigDecimal;
import java.time.Instant;

public record VehicleModelResponse(
        Long id,
        String modelCode,
        String name,
        VehicleCategory category,
        Powertrain powertrain,
        Integer launchYear,
        BigDecimal basePrice,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleModelResponse from(VehicleModel model) {
        return new VehicleModelResponse(
                model.getId(),
                model.getModelCode(),
                model.getName(),
                model.getCategory(),
                model.getPowertrain(),
                model.getLaunchYear(),
                model.getBasePrice(),
                model.isActive(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }
}
