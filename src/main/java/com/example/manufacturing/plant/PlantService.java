package com.example.manufacturing.plant;

import com.example.manufacturing.common.exception.BusinessRuleException;
import com.example.manufacturing.common.exception.DuplicateResourceException;
import com.example.manufacturing.common.exception.ResourceNotFoundException;
import com.example.manufacturing.plant.dto.PlantRequest;
import com.example.manufacturing.plant.dto.PlantResponse;
import com.example.manufacturing.productionorder.ProductionOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlantService {

    private final PlantRepository plantRepository;
    private final ProductionOrderRepository productionOrderRepository;

    public PlantService(PlantRepository plantRepository, ProductionOrderRepository productionOrderRepository) {
        this.plantRepository = plantRepository;
        this.productionOrderRepository = productionOrderRepository;
    }

    public Page<PlantResponse> findAll(PlantStatus status, Pageable pageable) {
        Page<Plant> plants = (status == null)
                ? plantRepository.findAll(pageable)
                : plantRepository.findByStatus(status, pageable);
        return plants.map(PlantResponse::from);
    }

    public PlantResponse findById(Long id) {
        return PlantResponse.from(getPlant(id));
    }

    @Transactional
    public PlantResponse create(PlantRequest request) {
        if (plantRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Plant with code '%s' already exists".formatted(request.code()));
        }
        Plant plant = new Plant();
        apply(plant, request);
        if (plant.getStatus() == null) {
            plant.setStatus(PlantStatus.ACTIVE);
        }
        return PlantResponse.from(plantRepository.save(plant));
    }

    @Transactional
    public PlantResponse update(Long id, PlantRequest request) {
        Plant plant = getPlant(id);
        if (plantRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new DuplicateResourceException("Plant with code '%s' already exists".formatted(request.code()));
        }
        PlantStatus currentStatus = plant.getStatus();
        apply(plant, request);
        if (request.status() == null) {
            plant.setStatus(currentStatus);
        }
        return PlantResponse.from(plantRepository.saveAndFlush(plant));
    }

    @Transactional
    public void delete(Long id) {
        Plant plant = getPlant(id);
        if (productionOrderRepository.existsByPlantId(id)) {
            throw new BusinessRuleException(
                    "Plant '%s' has production orders and cannot be deleted. Set its status to INACTIVE instead."
                            .formatted(plant.getCode()));
        }
        plantRepository.delete(plant);
    }

    /** Used by other services (e.g. production orders) to load a plant or fail with 404. */
    public Plant getPlant(Long id) {
        return plantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plant", id));
    }

    private static void apply(Plant plant, PlantRequest request) {
        plant.setCode(request.code());
        plant.setName(request.name());
        plant.setCity(request.city());
        plant.setCountry(request.country());
        plant.setDailyCapacity(request.dailyCapacity());
        plant.setStatus(request.status());
    }
}
