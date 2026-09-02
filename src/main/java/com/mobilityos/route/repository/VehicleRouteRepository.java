package com.mobilityos.route.repository;

import com.mobilityos.route.entity.VehicleRoute;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRouteRepository
        extends JpaRepository<VehicleRoute, Long> {

    @EntityGraph(attributePaths = {
            "vehicle",
            "setByUser"
    })
    Optional<VehicleRoute>
    findByVehicleIdAndStatus(
            Long vehicleId,
            VehicleRoute.RouteStatus status
    );

    @EntityGraph(attributePaths = {
            "vehicle",
            "setByUser"
    })
    List<VehicleRoute>
    findByVehicleIdOrderByStartedAtDesc(
            Long vehicleId
    );
}