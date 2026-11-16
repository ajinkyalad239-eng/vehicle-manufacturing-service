package com.example.manufacturing.support;

import com.example.manufacturing.plant.Plant;
import com.example.manufacturing.plant.PlantStatus;
import com.example.manufacturing.productionorder.OrderPriority;
import com.example.manufacturing.productionorder.ProductionOrder;
import com.example.manufacturing.productionorder.ProductionOrderStatus;
import com.example.manufacturing.vehiclemodel.Powertrain;
import com.example.manufacturing.vehiclemodel.VehicleCategory;
import com.example.manufacturing.vehiclemodel.VehicleModel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Builders for entities used across unit tests. */
public final class TestData {

    public static final LocalDate START = LocalDate.of(2026, 12, 1);

    private TestData() {
    }

    public static Plant plant(Long id, PlantStatus status, int dailyCapacity) {
        Plant plant = new Plant();
        plant.setId(id);
        plant.setCode("JP-TST-" + id);
        plant.setName("Test Plant " + id);
        plant.setCity("Yokohama");
        plant.setCountry("Japan");
        plant.setDailyCapacity(dailyCapacity);
        plant.setStatus(status);
        return plant;
    }

    public static Plant activePlant() {
        return plant(1L, PlantStatus.ACTIVE, 100);
    }

    public static VehicleModel vehicleModel(Long id, boolean active) {
        VehicleModel model = new VehicleModel();
        model.setId(id);
        model.setModelCode("TST-" + id);
        model.setName("Test Model " + id);
        model.setCategory(VehicleCategory.CROSSOVER);
        model.setPowertrain(Powertrain.ELECTRIC);
        model.setLaunchYear(2025);
        model.setBasePrice(new BigDecimal("4250000.00"));
        model.setActive(active);
        return model;
    }

    public static VehicleModel activeModel() {
        return vehicleModel(10L, true);
    }

    public static ProductionOrder order(Long id, ProductionOrderStatus status, int quantity, int completed) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setOrderNumber("PO-TEST-" + id);
        order.setPlant(activePlant());
        order.setVehicleModel(activeModel());
        order.setQuantity(quantity);
        order.setCompletedQuantity(completed);
        order.setStatus(status);
        order.setPriority(OrderPriority.MEDIUM);
        order.setPlannedStartDate(START);
        order.setPlannedEndDate(START.plusDays(9));
        return order;
    }
}
