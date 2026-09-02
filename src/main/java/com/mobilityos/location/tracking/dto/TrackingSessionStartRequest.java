package com.mobilityos.location.tracking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TrackingSessionStartRequest(

        @NotNull
        UUID deviceInstallationId

) {
}