package com.mobilityos.location.tracking;

import com.mobilityos.fleet.membership.VehicleMemberRepository;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TrackingRuntimeActivationCoordinator {

    private final VehicleMemberRepository
            vehicleMemberRepository;

    private final VehicleRepository
            vehicleRepository;

    private final VehicleTrackingSessionRepository
            trackingSessionRepository;

    private final VehicleTrackingRuntimeStore
            runtimeStore;

    public TrackingRuntimeActivationCoordinator(
            VehicleMemberRepository vehicleMemberRepository,
            VehicleRepository vehicleRepository,
            VehicleTrackingSessionRepository trackingSessionRepository,
            VehicleTrackingRuntimeStore runtimeStore
    ) {
        this.vehicleMemberRepository =
                Objects.requireNonNull(
                        vehicleMemberRepository,
                        "vehicleMemberRepository must not be null"
                );

        this.vehicleRepository =
                Objects.requireNonNull(
                        vehicleRepository,
                        "vehicleRepository must not be null"
                );

        this.trackingSessionRepository =
                Objects.requireNonNull(
                        trackingSessionRepository,
                        "trackingSessionRepository must not be null"
                );

        this.runtimeStore =
                Objects.requireNonNull(
                        runtimeStore,
                        "runtimeStore must not be null"
                );
    }

    // =========================================================
    // AFTER-COMMIT AUTHORITY ESTABLISHMENT
    // =========================================================

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void activateIfStillAuthorized(
            Long vehicleId,
            UUID sessionId,
            Long userId
    ) {
        Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );

        Objects.requireNonNull(
                sessionId,
                "sessionId must not be null"
        );

        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        /*
         * Reacquire the VehicleMember lifecycle lock.
         *
         * If membership was revoked while the original tracking
         * transaction was committing, do not recreate Redis
         * authority.
         */
        if (vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        vehicleId,
                        userId
                )
                .isEmpty()) {

            return;
        }

        /*
         * Reacquire the vehicle lifecycle lock too.
         *
         * This serializes delayed runtime activation against
         * vehicle deactivation.
         */
        Vehicle vehicle =
                vehicleRepository
                        .findByIdForUpdate(
                                vehicleId
                        )
                        .orElse(null);

        if (vehicle == null
                || !vehicle.isActive()) {

            return;
        }

        /*
         * Reread the committed session.
         *
         * A session may have been ended after its creation
         * transaction committed but before this callback obtained
         * lifecycle authority.
         */
        VehicleTrackingSession session =
                trackingSessionRepository
                        .findByIdAndVehicleIdAndUserId(
                                sessionId,
                                vehicleId,
                                userId
                        )
                        .orElse(null);

        if (session == null
                || session.getStatus()
                != TrackingSessionStatus.ACTIVE) {

            return;
        }

        /*
         * Redis activation happens while the database lifecycle
         * locks remain held.
         *
         * Therefore vehicle deactivation or membership revocation
         * cannot pass this point and leave stale authority behind.
         */
        runtimeStore.activateNewSession(
                vehicleId,
                sessionId,
                userId,
                session.getDeviceInstallationId(),
                session.getStartedAt()
        );
    }
}