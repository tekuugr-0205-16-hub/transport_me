package com.mobilityos.location.tracking.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record VehicleTrackingRuntimeState(

        Long vehicleId,
        UUID sessionId,
        Long userId,
        UUID deviceInstallationId,
        long latestSequence,
        Instant startedAt,
        Instant lastActivityAt

) {

    public VehicleTrackingRuntimeState {

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

        Objects.requireNonNull(
                deviceInstallationId,
                "deviceInstallationId must not be null"
        );

        Objects.requireNonNull(
                startedAt,
                "startedAt must not be null"
        );

        Objects.requireNonNull(
                lastActivityAt,
                "lastActivityAt must not be null"
        );

        if (latestSequence < 0) {
            throw new IllegalArgumentException(
                    "latestSequence must not be negative"
            );
        }
    }

    public static VehicleTrackingRuntimeState initial(
            Long vehicleId,
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            Instant startedAt
    ) {
        return new VehicleTrackingRuntimeState(
                vehicleId,
                sessionId,
                userId,
                deviceInstallationId,
                0L,
                startedAt,
                startedAt
        );
    }
}