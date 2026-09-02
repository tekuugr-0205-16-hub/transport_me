package com.mobilityos.location.quality;

import com.mobilityos.location.dto.GpsPingRequest;
import com.mobilityos.location.observation.LocationObservation;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class LocationQualityPolicy {

    private static final double MAX_ACCEPTABLE_ACCURACY_METERS =
            100.0;

    private static final Duration FUTURE_CLOCK_TOLERANCE =
            Duration.ofMinutes(2);

    public boolean isAcceptable(
            GpsPingRequest request,
            Instant now
    ) {
        if (request == null) {
            return false;
        }

        return isAcceptable(
                request.accuracyMeters(),
                request.recordedAt(),
                now
        );
    }

    public boolean isAcceptable(
            LocationObservation observation,
            Instant now
    ) {
        if (observation == null) {
            return false;
        }

        return isAcceptable(
                observation.accuracyMeters(),
                observation.recordedAt(),
                now
        );
    }

    private boolean isAcceptable(
            Double accuracyMeters,
            Instant recordedAt,
            Instant now
    ) {
        if (now == null) {
            return false;
        }

        if (accuracyMeters != null
                && accuracyMeters
                > MAX_ACCEPTABLE_ACCURACY_METERS) {

            return false;
        }

        if (recordedAt != null
                && recordedAt.isAfter(
                now.plus(
                        FUTURE_CLOCK_TOLERANCE
                )
        )) {

            return false;
        }

        return true;
    }
}