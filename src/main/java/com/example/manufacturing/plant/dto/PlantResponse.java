package com.example.manufacturing.plant.dto;

import com.example.manufacturing.plant.Plant;
import com.example.manufacturing.plant.PlantStatus;

import java.time.Instant;

public record PlantResponse(
        Long id,
        String code,
        String name,
        String city,
        String country,
        Integer dailyCapacity,
        PlantStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantResponse from(Plant plant) {
        return new PlantResponse(
                plant.getId(),
                plant.getCode(),
                plant.getName(),
                plant.getCity(),
                plant.getCountry(),
                plant.getDailyCapacity(),
                plant.getStatus(),
                plant.getCreatedAt(),
                plant.getUpdatedAt());
    }
}
