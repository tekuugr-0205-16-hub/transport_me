package com.mobilityos.fleet.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository
        extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlateNumber(
            String plateNumber
    );

    boolean existsByPlateNumber(
            String plateNumber
    );

    List<Vehicle> findByOperatorId(
            Long organizationId
    );
}