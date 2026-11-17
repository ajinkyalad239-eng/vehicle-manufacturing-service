package com.example.manufacturing.productionorder;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.plant.Plant;
import com.example.manufacturing.plant.PlantService;
import com.example.manufacturing.plant.PlantStatus;
import com.example.manufacturing.productionorder.dto.ProductionOrderRequest;
import com.example.manufacturing.productionorder.dto.ProductionOrderResponse;
import com.example.manufacturing.vehiclemodel.VehicleModel;
import com.example.manufacturing.vehiclemodel.VehicleModelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductionOrderService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final ProductionOrderRepository orderRepository;
    private final PlantService plantService;
    private final VehicleModelService vehicleModelService;

    public ProductionOrderService(ProductionOrderRepository orderRepository,
                                  PlantService plantService,
                                  VehicleModelService vehicleModelService) {
        this.orderRepository = orderRepository;
        this.plantService = plantService;
        this.vehicleModelService = vehicleModelService;
    }

    public Page<ProductionOrderResponse> findAll(ProductionOrderStatus status, Long plantId,
                                                 Long vehicleModelId, Pageable pageable) {
        return orderRepository.search(status, plantId, vehicleModelId, pageable)
                .map(ProductionOrderResponse::from);
    }

    public ProductionOrderResponse findById(Long id) {
        return ProductionOrderResponse.from(getOrder(id));
    }

    @Transactional
    public ProductionOrderResponse create(ProductionOrderRequest request) {
        Plant plant = plantService.getPlant(request.plantId());
        VehicleModel model = vehicleModelService.getVehicleModel(request.vehicleModelId());
        validate(request, plant, model);

        ProductionOrder order = new ProductionOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(ProductionOrderStatus.PLANNED);
        order.setCompletedQuantity(0);
        apply(order, request, plant, model);
        return ProductionOrderResponse.from(orderRepository.save(order));
    }

    /** Orders can only be re-planned while they are still PLANNED. */
    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderRequest request) {
        ProductionOrder order = getOrder(id);
        if (order.getStatus() != ProductionOrderStatus.PLANNED) {
            throw new BusinessRuleException(
                    "Only PLANNED orders can be edited; order %s is %s"
                            .formatted(order.getOrderNumber(), order.getStatus()));
        }
        Plant plant = plantService.getPlant(request.plantId());
        VehicleModel model = vehicleModelService.getVehicleModel(request.vehicleModelId());
        validate(request, plant, model);
        apply(order, request, plant, model);
        return ProductionOrderResponse.from(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public ProductionOrderResponse changeStatus(Long id, ProductionOrderStatus target) {
        ProductionOrder order = getOrder(id);
        ProductionOrderStatus current = order.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "Cannot change order %s from %s to %s".formatted(order.getOrderNumber(), current, target));
        }
        if (target == ProductionOrderStatus.IN_PROGRESS && order.getActualStartAt() == null) {
            order.setActualStartAt(now());
        }
        if (target == ProductionOrderStatus.COMPLETED) {
            order.setCompletedQuantity(order.getQuantity());
            order.setActualEndAt(now());
        }
        if (target == ProductionOrderStatus.CANCELLED) {
            order.setActualEndAt(now());
        }
        order.setStatus(target);
        return ProductionOrderResponse.from(orderRepository.saveAndFlush(order));
    }

    /** Records how many vehicles have been built. Reaching the target quantity completes the order. */
    @Transactional
    public ProductionOrderResponse updateProgress(Long id, int completedQuantity) {
        ProductionOrder order = getOrder(id);
        if (order.getStatus() != ProductionOrderStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Progress can only be reported for IN_PROGRESS orders; order %s is %s"
                            .formatted(order.getOrderNumber(), order.getStatus()));
        }
        if (completedQuantity > order.getQuantity()) {
            throw new BusinessRuleException(
                    "Completed quantity %d exceeds ordered quantity %d"
                            .formatted(completedQuantity, order.getQuantity()));
        }
        if (completedQuantity < order.getCompletedQuantity()) {
            throw new BusinessRuleException(
                    "Completed quantity cannot go down (currently %d)".formatted(order.getCompletedQuantity()));
        }
        order.setCompletedQuantity(completedQuantity);
        if (completedQuantity == order.getQuantity()) {
            order.setStatus(ProductionOrderStatus.COMPLETED);
            order.setActualEndAt(now());
        }
        return ProductionOrderResponse.from(orderRepository.saveAndFlush(order));
    }

    /** Only orders that never started (PLANNED) or were CANCELLED can be deleted. */
    @Transactional
    public void delete(Long id) {
        ProductionOrder order = getOrder(id);
        if (order.getStatus() != ProductionOrderStatus.PLANNED
                && order.getStatus() != ProductionOrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Order %s is %s and cannot be deleted; cancel it first"
                            .formatted(order.getOrderNumber(), order.getStatus()));
        }
        orderRepository.delete(order);
    }

    private ProductionOrder getOrder(Long id) {
        return orderRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production order", id));
    }

    private static void validate(ProductionOrderRequest request, Plant plant, VehicleModel model) {
        if (plant.getStatus() != PlantStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Plant '%s' is %s and cannot take new orders".formatted(plant.getCode(), plant.getStatus()));
        }
        if (!model.isActive()) {
            throw new BusinessRuleException(
                    "Vehicle model '%s' is not active".formatted(model.getModelCode()));
        }
        LocalDate start = request.plannedStartDate();
        LocalDate end = request.plannedEndDate();
        if (end.isBefore(start)) {
            throw new BusinessRuleException("plannedEndDate must be on or after plannedStartDate");
        }
        // Capacity check: can the plant build this many vehicles in the planned window?
        long plannedDays = ChronoUnit.DAYS.between(start, end) + 1;
        long requiredDays = (request.quantity() + plant.getDailyCapacity() - 1) / plant.getDailyCapacity();
        if (requiredDays > plannedDays) {
            throw new BusinessRuleException(
                    "Plant '%s' builds %d vehicles/day, so %d vehicles need at least %d days, but only %d are planned"
                            .formatted(plant.getCode(), plant.getDailyCapacity(), request.quantity(),
                                    requiredDays, plannedDays));
        }
    }

    private static void apply(ProductionOrder order, ProductionOrderRequest request, Plant plant, VehicleModel model) {
        order.setPlant(plant);
        order.setVehicleModel(model);
        order.setQuantity(request.quantity());
        order.setPriority(request.priority() == null ? OrderPriority.MEDIUM : request.priority());
        order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedEndDate(request.plannedEndDate());
        order.setNotes(request.notes());
    }

    /**
     * Current time at the precision PostgreSQL stores (microseconds). Without this, the response
     * right after an update could show more digits (e.g. Windows clocks give 100 ns) than the same
     * value read back later, so the API would return two different values for one timestamp.
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** e.g. PO-20261116-3F9A1C */
    private String generateOrderNumber() {
        String candidate;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            candidate = "PO-" + LocalDate.now().format(ORDER_DATE) + "-" + suffix;
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
