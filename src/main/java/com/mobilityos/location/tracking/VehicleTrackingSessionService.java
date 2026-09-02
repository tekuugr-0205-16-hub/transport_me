package com.mobilityos.location.tracking;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.tracking.dto.TrackingSessionResponse;
import com.mobilityos.location.tracking.runtime.RuntimeEnsureResult;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class VehicleTrackingSessionService {

    private final VehicleTrackingSessionRepository trackingSessionRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final FleetAccessService fleetAccessService;
    private final VehicleTrackingRuntimeStore runtimeStore;

    public VehicleTrackingSessionService(
            VehicleTrackingSessionRepository trackingSessionRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            FleetAccessService fleetAccessService,
            VehicleTrackingRuntimeStore runtimeStore
    ) {
        this.trackingSessionRepository =
                trackingSessionRepository;

        this.vehicleRepository =
                vehicleRepository;

        this.userRepository =
                userRepository;

        this.fleetAccessService =
                fleetAccessService;

        this.runtimeStore =
                runtimeStore;
    }

    // =========================================================
    // START / RESUME
    // =========================================================

    @Transactional
    public TrackingSessionResponse startSession(
            Long currentUserId,
            Long vehicleId,
            UUID deviceInstallationId
    ) {
        Objects.requireNonNull(
                deviceInstallationId,
                "deviceInstallationId must not be null"
        );

        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        Vehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle not found"
                                )
                        );

        if (!"ACTIVE".equals(
                vehicle.getStatus().name()
        )) {
            throw new ConflictException(
                    "Only an active vehicle can start a tracking session"
            );
        }

        // =====================================================
        // ACTIVE SESSION BY VEHICLE
        // =====================================================

        var vehicleSession =
                trackingSessionRepository
                        .findByVehicleIdAndStatus(
                                vehicleId,
                                TrackingSessionStatus.ACTIVE
                        );

        if (vehicleSession.isPresent()) {

            VehicleTrackingSession existing =
                    vehicleSession.get();

            if (!currentUserId.equals(
                    existing
                            .getUser()
                            .getId()
            )) {
                throw new ConflictException(
                        "This vehicle is already being operated by another user"
                );
            }

            if (!deviceInstallationId.equals(
                    existing.getDeviceInstallationId()
            )) {
                throw new ConflictException(
                        "This tracking session is active on another device"
                );
            }

            return resumeOrRecover(
                    existing
            );
        }

        // =====================================================
        // ACTIVE SESSION BY USER
        // =====================================================

        var userSession =
                trackingSessionRepository
                        .findByUserIdAndStatus(
                                currentUserId,
                                TrackingSessionStatus.ACTIVE
                        );

        if (userSession.isPresent()) {

            VehicleTrackingSession existing =
                    userSession.get();

            if (!vehicleId.equals(
                    existing
                            .getVehicle()
                            .getId()
            )) {
                throw new ConflictException(
                        "You already have an active tracking session for another vehicle"
                );
            }

            if (!deviceInstallationId.equals(
                    existing.getDeviceInstallationId()
            )) {
                throw new ConflictException(
                        "This tracking session is active on another device"
                );
            }

            return resumeOrRecover(
                    existing
            );
        }

        // =====================================================
        // ACTIVE SESSION BY DEVICE
        // =====================================================

        var deviceSession =
                trackingSessionRepository
                        .findByDeviceInstallationIdAndStatus(
                                deviceInstallationId,
                                TrackingSessionStatus.ACTIVE
                        );

        if (deviceSession.isPresent()) {

            VehicleTrackingSession existing =
                    deviceSession.get();

            /*
             * Defensive consistency case.
             *
             * Under normal database consistency the vehicle lookup
             * above would already have discovered this session.
             */
            if (currentUserId.equals(
                    existing
                            .getUser()
                            .getId()
            )
                    &&
                    vehicleId.equals(
                            existing
                                    .getVehicle()
                                    .getId()
                    )) {

                return resumeOrRecover(
                        existing
                );
            }

            throw new ConflictException(
                    "This device already has an active tracking session"
            );
        }

        // =====================================================
        // CREATE FIRST SESSION
        // =====================================================

        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found"
                                )
                        );

        return createNewSession(
                vehicle,
                user,
                deviceInstallationId
        );
    }

    // =========================================================
    // CURRENT ACTIVE SESSION
    // =========================================================

    @Transactional(readOnly = true)
    public TrackingSessionResponse getActiveSession(
            Long currentUserId,
            Long vehicleId
    ) {
        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        VehicleTrackingSession session =
                trackingSessionRepository
                        .findByVehicleIdAndStatus(
                                vehicleId,
                                TrackingSessionStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No active tracking session found"
                                )
                        );

        /*
         * A read/status request may repair safe structural metadata,
         * but it may NEVER recreate lost sequence authority.
         */
        requireRuntimeContinuity(
                session
        );

        return TrackingSessionResponse.from(
                session
        );
    }

    // =========================================================
    // END OWN SESSION
    // =========================================================

    @Transactional
    public TrackingSessionResponse endSession(
            Long currentUserId,
            Long vehicleId,
            UUID sessionId
    ) {
        VehicleTrackingSession session =
                trackingSessionRepository
                        .findByIdAndVehicleIdAndUserId(
                                sessionId,
                                vehicleId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Tracking session not found"
                                )
                        );

        session.end(
                Instant.now(),
                TrackingSessionEndReason.USER_ENDED
        );

        deactivateRuntime(
                session
        );

        return TrackingSessionResponse.from(
                session
        );
    }

    // =========================================================
    // INTERNAL TERMINATION
    // =========================================================

    @Transactional
    public void terminateActiveSession(
            Long vehicleId,
            Long userId,
            TrackingSessionEndReason reason
    ) {
        Objects.requireNonNull(
                reason,
                "Tracking session end reason is required"
        );

        trackingSessionRepository
                .findByUserIdAndStatus(
                        userId,
                        TrackingSessionStatus.ACTIVE
                )
                .filter(session ->
                        vehicleId.equals(
                                session
                                        .getVehicle()
                                        .getId()
                        )
                )
                .ifPresent(session -> {

                    session.end(
                            Instant.now(),
                            reason
                    );

                    deactivateRuntime(
                            session
                    );
                });
    }

    // =========================================================
    // RESUME / REDIS LOSS RECOVERY
    // =========================================================

    private TrackingSessionResponse resumeOrRecover(
            VehicleTrackingSession existing
    ) {
        RuntimeEnsureResult result =
                inspectRuntime(
                        existing
                );

        return switch (result) {

            case ALREADY_ACTIVE ->
                    TrackingSessionResponse.from(
                            existing
                    );

            case MISSING_RUNTIME ->
                    rotateSessionAfterRuntimeLoss(
                            existing
                    );

            case CONFLICTING_RUNTIME ->
                    throw new ConflictException(
                            "Tracking runtime changed concurrently; retry the request"
                    );
        };
    }

    /*
     * Redis has lost the sequence checkpoint for this session.
     *
     * We intentionally do NOT rebuild the same session UUID
     * with latestSequence = 0.
     *
     * Instead:
     *
     * old session A -> ENDED
     * new session B -> ACTIVE
     *
     * Old queued observations still contain session A and are
     * rejected by the Batch 13.11 session-authority check.
     */
    private TrackingSessionResponse rotateSessionAfterRuntimeLoss(
            VehicleTrackingSession existing
    ) {
        try {
            existing.end(
                    Instant.now(),
                    TrackingSessionEndReason.SESSION_TIMEOUT
            );

            /*
             * Flush the old ACTIVE -> ENDED transition first.
             *
             * This matters because PostgreSQL has partial unique
             * indexes allowing only one ACTIVE session per:
             *
             * vehicle
             * user
             * device
             */
            trackingSessionRepository
                    .saveAndFlush(
                            existing
                    );

            /*
             * Delete only if Redis still belongs to the old
             * session. A newer concurrent runtime must survive.
             */
            deactivateRuntime(
                    existing
            );

            VehicleTrackingSession replacement =
                    new VehicleTrackingSession(
                            existing.getVehicle(),
                            existing.getUser(),
                            existing.getDeviceInstallationId()
                    );

            VehicleTrackingSession saved =
                    trackingSessionRepository
                            .saveAndFlush(
                                    replacement
                            );

            activateNewRuntimeAfterCommit(
                    saved
            );

            return TrackingSessionResponse.from(
                    saved
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * Another request may have recovered or changed
             * the session concurrently.
             *
             * PostgreSQL remains the final concurrency authority.
             */
            throw new ConflictException(
                    "Tracking session changed concurrently; retry the request"
            );
        }
    }

    // =========================================================
    // CREATE
    // =========================================================

    private TrackingSessionResponse createNewSession(
            Vehicle vehicle,
            User user,
            UUID deviceInstallationId
    ) {
        VehicleTrackingSession session =
                new VehicleTrackingSession(
                        vehicle,
                        user,
                        deviceInstallationId
                );

        try {
            VehicleTrackingSession saved =
                    trackingSessionRepository
                            .saveAndFlush(
                                    session
                            );

            activateNewRuntimeAfterCommit(
                    saved
            );

            return TrackingSessionResponse.from(
                    saved
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * Database remains final authority for:
             *
             * one ACTIVE session per vehicle
             * one ACTIVE session per user
             * one ACTIVE session per device
             */
            throw new ConflictException(
                    "Tracking session changed concurrently; retry the request"
            );
        }
    }

    // =========================================================
    // RUNTIME
    // =========================================================

    private RuntimeEnsureResult inspectRuntime(
            VehicleTrackingSession session
    ) {
        return runtimeStore.ensureActiveSession(
                session
                        .getVehicle()
                        .getId(),

                session.getId(),

                session
                        .getUser()
                        .getId(),

                session.getDeviceInstallationId(),

                session.getStartedAt()
        );
    }

    private void requireRuntimeContinuity(
            VehicleTrackingSession session
    ) {
        RuntimeEnsureResult result =
                inspectRuntime(
                        session
                );

        if (result
                == RuntimeEnsureResult.MISSING_RUNTIME) {

            throw new ConflictException(
                    "Tracking runtime continuity was lost; start tracking again"
            );
        }

        if (result
                == RuntimeEnsureResult.CONFLICTING_RUNTIME) {

            throw new ConflictException(
                    "Tracking runtime changed concurrently; retry the request"
            );
        }
    }

    private void deactivateRuntime(
            VehicleTrackingSession session
    ) {
        runtimeStore.deactivateIfMatches(
                session
                        .getVehicle()
                        .getId(),

                session.getId()
        );
    }

    // =========================================================
    // AFTER-COMMIT REDIS ACTIVATION
    // =========================================================

    private void activateNewRuntimeAfterCommit(
            VehicleTrackingSession session
    ) {
        Long vehicleId =
                session
                        .getVehicle()
                        .getId();

        UUID sessionId =
                session.getId();

        Long userId =
                session
                        .getUser()
                        .getId();

        UUID deviceInstallationId =
                session.getDeviceInstallationId();

        Instant startedAt =
                session.getStartedAt();

        Runnable activation =
                () ->
                        runtimeStore
                                .activateNewSession(
                                        vehicleId,
                                        sessionId,
                                        userId,
                                        deviceInstallationId,
                                        startedAt
                                );

        if (TransactionSynchronizationManager
                .isActualTransactionActive()
                &&
                TransactionSynchronizationManager
                        .isSynchronizationActive()) {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {

                                @Override
                                public void afterCommit() {
                                    activation.run();
                                }
                            }
                    );

            return;
        }

        activation.run();
    }
}