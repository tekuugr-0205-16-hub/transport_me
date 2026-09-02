package com.mobilityos.location.observation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LocationObservation(

        UUID observationId,

        UUID trackingSessionId,

        long sequenceNumber,

        Long vehicleId,

        Long submittedByUserId,

        UUID deviceInstallationId,

        double latitude,

        double longitude,

        Double speedMetersPerSecond,

        Double headingDegrees,

        double accuracyMeters,

        Instant recordedAt,

        Instant receivedAt,

        LocationSource source

) {

    public LocationObservation {

        Objects.requireNonNull(
                observationId,
                "observationId must not be null"
        );

        Objects.requireNonNull(
                trackingSessionId,
                "trackingSessionId must not be null"
        );

        Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );

        Objects.requireNonNull(
                submittedByUserId,
                "submittedByUserId must not be null"
        );

        Objects.requireNonNull(
                deviceInstallationId,
                "deviceInstallationId must not be null"
        );

        Objects.requireNonNull(
                recordedAt,
                "recordedAt must not be null"
        );

        Objects.requireNonNull(
                receivedAt,
                "receivedAt must not be null"
        );

        Objects.requireNonNull(
                source,
                "source must not be null"
        );

        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException(
                    "sequenceNumber must be greater than zero"
            );
        }

        requireFinite(
                latitude,
                "latitude"
        );

        requireFinite(
                longitude,
                "longitude"
        );

        if (latitude < -90.0
                || latitude > 90.0) {

            throw new IllegalArgumentException(
                    "latitude must be between -90 and 90"
            );
        }

        if (longitude < -180.0
                || longitude > 180.0) {

            throw new IllegalArgumentException(
                    "longitude must be between -180 and 180"
            );
        }

        if (speedMetersPerSecond != null) {

            requireFinite(
                    speedMetersPerSecond,
                    "speedMetersPerSecond"
            );

            if (speedMetersPerSecond < 0.0) {

                throw new IllegalArgumentException(
                        "speedMetersPerSecond must not be negative"
                );
            }
        }

        if (headingDegrees != null) {

            requireFinite(
                    headingDegrees,
                    "headingDegrees"
            );

            if (headingDegrees < 0.0
                    || headingDegrees >= 360.0) {

                throw new IllegalArgumentException(
                        "headingDegrees must be in [0, 360)"
                );
            }
        }

        requireFinite(
                accuracyMeters,
                "accuracyMeters"
        );

        if (accuracyMeters < 0.0) {

            throw new IllegalArgumentException(
                    "accuracyMeters must not be negative"
            );
        }
    }

    private static void requireFinite(
            double value,
            String field
    ) {

        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    field + " must be finite"
            );
        }
    }
}