package com.mobilityos.location.observation;

import com.mobilityos.location.dto.LocationObservationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class LocationObservationFactory {

    public LocationObservation createMobileObservation(
            Long currentUserId,
            Long vehicleId,
            LocationObservationRequest request,
            Instant receivedAt
    ) {
        Objects.requireNonNull(
                currentUserId,
                "currentUserId must not be null"
        );

        Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Objects.requireNonNull(
                receivedAt,
                "receivedAt must not be null"
        );

        return new LocationObservation(
                request.observationId(),
                request.trackingSessionId(),
                request.sequenceNumber(),
                vehicleId,
                currentUserId,
                request.deviceInstallationId(),
                request.latitude(),
                request.longitude(),
                request.speedMetersPerSecond(),
                request.headingDegrees(),
                request.accuracyMeters(),
                request.recordedAt(),
                receivedAt,
                LocationSource.MOBILE
        );
    }
}