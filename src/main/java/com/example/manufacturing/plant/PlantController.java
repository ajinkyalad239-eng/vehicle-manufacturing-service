package com.example.manufacturing.plant;

import com.example.manufacturing.common.dto.PageResponse;
import com.example.manufacturing.plant.dto.PlantRequest;
import com.example.manufacturing.plant.dto.PlantResponse;
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
@RequestMapping("/api/v1/plants")
@Tag(name = "Plants", description = "Assembly plants")
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    @GetMapping
    @Operation(summary = "List plants (paginated, optional status filter)")
    public PageResponse<PlantResponse> list(
            @RequestParam(required = false) PlantStatus status,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(plantService.findAll(status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a plant by id")
    public PlantResponse get(@PathVariable Long id) {
        return plantService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a plant")
    public ResponseEntity<PlantResponse> create(@Valid @RequestBody PlantRequest request) {
        PlantResponse created = plantService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a plant")
    public PlantResponse update(@PathVariable Long id, @Valid @RequestBody PlantRequest request) {
        return plantService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a plant (only if it has no production orders)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        plantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
