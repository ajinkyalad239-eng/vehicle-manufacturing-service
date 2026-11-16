package com.example.manufacturing.productionorder;

import com.example.manufacturing.common.dto.PageResponse;
import com.example.manufacturing.productionorder.dto.ProductionOrderRequest;
import com.example.manufacturing.productionorder.dto.ProductionOrderResponse;
import com.example.manufacturing.productionorder.dto.ProgressUpdateRequest;
import com.example.manufacturing.productionorder.dto.StatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/production-orders")
@Tag(name = "Production orders", description = "Orders to build vehicles at a plant")
public class ProductionOrderController {

    private final ProductionOrderService orderService;

    public ProductionOrderController(ProductionOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List production orders (paginated, optional status/plant/model filters)")
    public PageResponse<ProductionOrderResponse> list(
            @RequestParam(required = false) ProductionOrderStatus status,
            @RequestParam(required = false) Long plantId,
            @RequestParam(required = false) Long vehicleModelId,
            @PageableDefault(size = 20, sort = "plannedStartDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(orderService.findAll(status, plantId, vehicleModelId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a production order by id")
    public ProductionOrderResponse get(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a production order (starts as PLANNED)")
    public ResponseEntity<ProductionOrderResponse> create(@Valid @RequestBody ProductionOrderRequest request) {
        ProductionOrderResponse created = orderService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Re-plan a production order (only while PLANNED)")
    public ProductionOrderResponse update(@PathVariable Long id, @Valid @RequestBody ProductionOrderRequest request) {
        return orderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order through its life-cycle (start, hold, complete, cancel)")
    public ProductionOrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return orderService.changeStatus(id, request.status());
    }

    @PatchMapping("/{id}/progress")
    @Operation(summary = "Report how many vehicles have been built so far")
    public ProductionOrderResponse updateProgress(@PathVariable Long id,
                                                  @Valid @RequestBody ProgressUpdateRequest request) {
        return orderService.updateProgress(id, request.completedQuantity());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a production order (only PLANNED or CANCELLED)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
