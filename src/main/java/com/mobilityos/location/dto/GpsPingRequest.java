package com.mobilityos.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record GpsPingRequest(

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @PositiveOrZero
        Double speed,

        @DecimalMin("0.0")
        @DecimalMax(value = "360.0", inclusive = false)
        Double heading,

        @PositiveOrZero
        Double accuracyMeters,

        Instant recordedAt

) {

        /*
         * Temporary backwards-compatible constructor.
         *
         * Existing tests/code that still create:
         *
         * new GpsPingRequest(lat, lon, speed, heading)
         *
         * will continue compiling.
         *
         * We will remove this later once Flutter always sends
         * accuracyMeters and recordedAt.
         */
        public GpsPingRequest(
                Double latitude,
                Double longitude,
                Double speed,
                Double heading
        ) {
                this(
                        latitude,
                        longitude,
                        speed,
                        heading,
                        null,
                        null
                );
        }
}