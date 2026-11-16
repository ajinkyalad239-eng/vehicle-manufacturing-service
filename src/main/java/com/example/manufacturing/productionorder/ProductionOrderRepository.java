package com.example.manufacturing.productionorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    /** Loads plant and vehicle model in the same query to avoid N+1 selects. */
    @EntityGraph(attributePaths = {"plant", "vehicleModel"})
    @Query("""
            select o from ProductionOrder o
            where (:status is null or o.status = :status)
              and (:plantId is null or o.plant.id = :plantId)
              and (:vehicleModelId is null or o.vehicleModel.id = :vehicleModelId)
            """)
    Page<ProductionOrder> search(@Param("status") ProductionOrderStatus status,
                                 @Param("plantId") Long plantId,
                                 @Param("vehicleModelId") Long vehicleModelId,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "vehicleModel"})
    Optional<ProductionOrder> findWithDetailsById(Long id);

    boolean existsByPlantId(Long plantId);

    boolean existsByVehicleModelId(Long vehicleModelId);

    boolean existsByOrderNumber(String orderNumber);
}
