package com.example.manufacturing.vehiclemodel;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.DuplicateResourceException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.productionorder.ProductionOrderRepository;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelRequest;
import com.example.manufacturing.vehiclemodel.dto.VehicleModelResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehicleModelService {

    private final VehicleModelRepository vehicleModelRepository;
    private final ProductionOrderRepository productionOrderRepository;

    public VehicleModelService(VehicleModelRepository vehicleModelRepository,
                               ProductionOrderRepository productionOrderRepository) {
        this.vehicleModelRepository = vehicleModelRepository;
        this.productionOrderRepository = productionOrderRepository;
    }

    public Page<VehicleModelResponse> findAll(Powertrain powertrain, Boolean active, Pageable pageable) {
        return vehicleModelRepository.search(powertrain, active, pageable).map(VehicleModelResponse::from);
    }

    public VehicleModelResponse findById(Long id) {
        return VehicleModelResponse.from(getVehicleModel(id));
    }

    @Transactional
    public VehicleModelResponse create(VehicleModelRequest request) {
        if (vehicleModelRepository.existsByModelCode(request.modelCode())) {
            throw new DuplicateResourceException(
                    "Vehicle model with code '%s' already exists".formatted(request.modelCode()));
        }
        VehicleModel model = new VehicleModel();
        apply(model, request);
        model.setActive(request.active() == null || request.active());
        return VehicleModelResponse.from(vehicleModelRepository.save(model));
    }

    @Transactional
    public VehicleModelResponse update(Long id, VehicleModelRequest request) {
        VehicleModel model = getVehicleModel(id);
        if (vehicleModelRepository.existsByModelCodeAndIdNot(request.modelCode(), id)) {
            throw new DuplicateResourceException(
                    "Vehicle model with code '%s' already exists".formatted(request.modelCode()));
        }
        apply(model, request);
        if (request.active() != null) {
            model.setActive(request.active());
        }
        return VehicleModelResponse.from(vehicleModelRepository.saveAndFlush(model));
    }

    @Transactional
    public void delete(Long id) {
        VehicleModel model = getVehicleModel(id);
        if (productionOrderRepository.existsByVehicleModelId(id)) {
            throw new BusinessRuleException(
                    "Vehicle model '%s' has production orders and cannot be deleted. Set active=false instead."
                            .formatted(model.getModelCode()));
        }
        vehicleModelRepository.delete(model);
    }

    public VehicleModel getVehicleModel(Long id) {
        return vehicleModelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle model", id));
    }

    private static void apply(VehicleModel model, VehicleModelRequest request) {
        model.setModelCode(request.modelCode());
        model.setName(request.name());
        model.setCategory(request.category());
        model.setPowertrain(request.powertrain());
        model.setLaunchYear(request.launchYear());
        model.setBasePrice(request.basePrice());
    }
}
