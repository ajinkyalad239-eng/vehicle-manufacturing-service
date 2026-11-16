package com.example.manufacturing.plant.dto;

import com.example.manufacturing.plant.PlantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body for POST /api/v1/plants and PUT /api/v1/plants/{id}. */
public record PlantRequest(

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "must contain only upper-case letters, digits and '-'")
        String code,

        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Size(max = 100)
        String city,

        @NotBlank @Size(max = 60)
        String country,

        @NotNull @Positive
        Integer dailyCapacity,

        /** Optional; defaults to ACTIVE when creating. */
        PlantStatus status) {
}
