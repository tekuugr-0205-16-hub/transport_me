package com.mobilityos.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record LocationObservationRequest(

        @NotNull
        UUID observationId,

        @NotNull
        UUID trackingSessionId,

        @NotNull
        @Positive
        Long sequenceNumber,

        @NotNull
        UUID deviceInstallationId,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @PositiveOrZero
        Double speedMetersPerSecond,

        @DecimalMin("0.0")
        @DecimalMax(
                value = "360.0",
                inclusive = false
        )
        Double headingDegrees,

        @NotNull
        @PositiveOrZero
        Double accuracyMeters,

        @NotNull
        Instant recordedAt

) {
}