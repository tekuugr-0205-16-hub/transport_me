package com.mobilityos.location.tracking.runtime;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VehicleTrackingRuntimeStore {

    boolean activateNewSession(
            Long vehicleId,
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            Instant startedAt
    );

    RuntimeEnsureResult ensureActiveSession(
            Long vehicleId,
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            Instant startedAt
    );

    Optional<VehicleTrackingRuntimeState> get(
            Long vehicleId
    );

    boolean deactivateIfMatches(
            Long vehicleId,
            UUID sessionId
    );
}