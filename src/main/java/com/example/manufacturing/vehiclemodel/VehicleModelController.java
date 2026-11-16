package com.example.manufacturing.vehiclemodel;

import com.example.manufacturing.common.dto.PageResponse;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelRequest;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/vehicle-models")
@Tag(name = "Vehicle models", description = "Vehicle models that plants can build")
public class VehicleModelController {

    private final VehicleModelService vehicleModelService;

    public VehicleModelController(VehicleModelService vehicleModelService) {
        this.vehicleModelService = vehicleModelService;
    }

    @GetMapping
    @Operation(summary = "List vehicle models (paginated, optional powertrain/active filters)")
    public PageResponse<VehicleModelResponse> list(
            @RequestParam(required = false) Powertrain powertrain,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "modelCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(vehicleModelService.findAll(powertrain, active, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a vehicle model by id")
    public VehicleModelResponse get(@PathVariable Long id) {
        return vehicleModelService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a vehicle model")
    public ResponseEntity<VehicleModelResponse> create(@Valid @RequestBody VehicleModelRequest request) {
        VehicleModelResponse created = vehicleModelService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vehicle model")
    public VehicleModelResponse update(@PathVariable Long id, @Valid @RequestBody VehicleModelRequest request) {
        return vehicleModelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vehicle model (only if it has no production orders)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehicleModelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
