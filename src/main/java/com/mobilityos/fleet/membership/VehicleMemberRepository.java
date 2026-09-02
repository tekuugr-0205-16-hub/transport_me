package com.mobilityos.fleet.membership;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleMemberRepository
        extends JpaRepository<VehicleMember, Long> {

    boolean existsByVehicleIdAndUserId(
            Long vehicleId,
            Long userId
    );

    Optional<VehicleMember> findByVehicleIdAndUserId(
            Long vehicleId,
            Long userId
    );

    @EntityGraph(attributePaths = "vehicle")
    List<VehicleMember> findByUserId(
            Long userId
    );

    @EntityGraph(attributePaths = "user")
    List<VehicleMember> findByVehicleId(
            Long vehicleId
    );
}