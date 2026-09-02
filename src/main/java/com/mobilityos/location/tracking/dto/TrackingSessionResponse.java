package com.mobilityos.location.tracking.dto;

import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.TrackingSessionStatus;
import com.mobilityos.location.tracking.VehicleTrackingSession;

import java.time.Instant;
import java.util.UUID;

public record TrackingSessionResponse(

        UUID sessionId,

        Long vehicleId,

        Long userId,

        TrackingSessionStatus status,

        Instant startedAt,

        Instant endedAt,

        TrackingSessionEndReason endReason

) {

    public static TrackingSessionResponse from(
            VehicleTrackingSession session
    ) {
        return new TrackingSessionResponse(
                session.getId(),
                session.getVehicle().getId(),
                session.getUser().getId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getEndReason()
        );
    }
}