package com.mobilityos.location.tracking;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleTrackingSessionRepository
        extends JpaRepository<VehicleTrackingSession, UUID> {

    @EntityGraph(attributePaths = {
            "vehicle",
            "user"
    })
    Optional<VehicleTrackingSession>
    findByVehicleIdAndStatus(
            Long vehicleId,
            TrackingSessionStatus status
    );

    @EntityGraph(attributePaths = {
            "vehicle",
            "user"
    })
    Optional<VehicleTrackingSession>
    findByUserIdAndStatus(
            Long userId,
            TrackingSessionStatus status
    );

    @EntityGraph(attributePaths = {
            "vehicle",
            "user"
    })
    Optional<VehicleTrackingSession>
    findByDeviceInstallationIdAndStatus(
            UUID deviceInstallationId,
            TrackingSessionStatus status
    );

    @EntityGraph(attributePaths = {
            "vehicle",
            "user"
    })
    Optional<VehicleTrackingSession>
    findByIdAndVehicleIdAndUserId(
            UUID sessionId,
            Long vehicleId,
            Long userId
    );
}