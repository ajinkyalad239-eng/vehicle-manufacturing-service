package com.example.manufacturing.vehiclemodel.dto;

import com.example.manufacturing.vehiclemodel.Powertrain;
import com.example.manufacturing.vehiclemodel.VehicleCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body for POST /api/v1/vehicle-models and PUT /api/v1/vehicle-models/{id}. */
public record VehicleModelRequest(

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "must contain only upper-case letters, digits and '-'")
        String modelCode,

        @NotBlank @Size(max = 100)
        String name,

        @NotNull
        VehicleCategory category,

        @NotNull
        Powertrain powertrain,

        @NotNull @Min(1950) @Max(2100)
        Integer launchYear,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        BigDecimal basePrice,

        /** Optional; defaults to true when creating. */
        Boolean active) {
}
