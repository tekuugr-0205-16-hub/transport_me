package com.mobilityos.location.tracking;

import com.mobilityos.fleet.membership.VehicleMemberRepository;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TrackingRuntimeActivationCoordinator {

    private final VehicleMemberRepository vehicleMemberRepository;

    private final VehicleTrackingSessionRepository
            trackingSessionRepository;

    private final VehicleTrackingRuntimeStore runtimeStore;

    public TrackingRuntimeActivationCoordinator(
            VehicleMemberRepository vehicleMemberRepository,
            VehicleTrackingSessionRepository trackingSessionRepository,
            VehicleTrackingRuntimeStore runtimeStore
    ) {
        this.vehicleMemberRepository =
                Objects.requireNonNull(
                        vehicleMemberRepository,
                        "vehicleMemberRepository must not be null"
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

    /*
     * This runs after the original tracking-session transaction
     * has committed.
     *
     * REQUIRES_NEW is deliberate:
     *
     * - obtain a fresh database transaction
     * - reacquire the VehicleMember lock
     * - verify authorization still exists
     * - verify the DB tracking session is still ACTIVE
     * - only then establish Redis runtime authority
     */
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
         * Reacquire exactly the same row lock used by:
         *
         * - tracking start
         * - membership revocation
         *
         * If membership was revoked after the original tracking
         * transaction committed, activation stops here.
         */
        if (vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        vehicleId,
                        userId
                )
                .isEmpty()) {

            return;
        }

        VehicleTrackingSession session =
                trackingSessionRepository
                        .findByIdAndVehicleIdAndUserId(
                                sessionId,
                                vehicleId,
                                userId
                        )
                        .orElse(null);

        /*
         * The session may have been ended while the after-commit
         * callback was waiting for the membership lock.
         */
        if (session == null
                || session.getStatus()
                != TrackingSessionStatus.ACTIVE) {

            return;
        }

        /*
         * Use the DB values we just reread instead of values
         * captured before commit.
         *
         * This also avoids timestamp precision assumptions
         * between Java Instant and PostgreSQL timestamptz.
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