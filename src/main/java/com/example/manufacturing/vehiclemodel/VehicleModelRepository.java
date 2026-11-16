package com.example.manufacturing.vehiclemodel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    @Query("""
            select m from VehicleModel m
            where (:powertrain is null or m.powertrain = :powertrain)
              and (:active is null or m.active = :active)
            """)
    Page<VehicleModel> search(@Param("powertrain") Powertrain powertrain,
                              @Param("active") Boolean active,
                              Pageable pageable);

    boolean existsByModelCode(String modelCode);

    boolean existsByModelCodeAndIdNot(String modelCode, Long id);
}
