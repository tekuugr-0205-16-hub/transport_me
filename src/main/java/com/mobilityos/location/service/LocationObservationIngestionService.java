package com.mobilityos.location.service;

import com.mobilityos.location.dto.LocationObservationRequest;
import com.mobilityos.location.live.LiveObservationResult;
import com.mobilityos.location.live.VehicleLiveStateStore;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationObservationFactory;
import com.mobilityos.location.observation.LocationObservationIngestionResult;
import com.mobilityos.location.quality.LocationQualityPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LocationObservationIngestionService {

    private final LocationObservationFactory observationFactory;

    private final LocationQualityPolicy locationQualityPolicy;

    private final VehicleLiveStateStore vehicleLiveStateStore;

    public LocationObservationIngestionService(
            LocationObservationFactory observationFactory,
            LocationQualityPolicy locationQualityPolicy,
            VehicleLiveStateStore vehicleLiveStateStore
    ) {
        this.observationFactory =
                observationFactory;

        this.locationQualityPolicy =
                locationQualityPolicy;

        this.vehicleLiveStateStore =
                vehicleLiveStateStore;
    }

    public LocationObservationIngestionResult ingest(
            Long currentUserId,
            Long vehicleId,
            LocationObservationRequest request
    ) {
        Instant receivedAt =
                Instant.now();

        LocationObservation observation =
                observationFactory
                        .createMobileObservation(
                                currentUserId,
                                vehicleId,
                                request,
                                receivedAt
                        );

        /*
         * Reject poor GPS before touching Redis.
         *
         * A bad observation must not advance the
         * authoritative sequence number.
         */
        if (!locationQualityPolicy.isAcceptable(
                observation,
                receivedAt
        )) {
            return LocationObservationIngestionResult
                    .REJECTED_QUALITY;
        }

        LiveObservationResult liveResult =
                vehicleLiveStateStore
                        .applyForLiveState(
                                observation
                        );

        return map(
                liveResult
        );
    }

    private LocationObservationIngestionResult map(
            LiveObservationResult result
    ) {
        return switch (result) {

            case APPLIED ->
                    LocationObservationIngestionResult
                            .APPLIED;

            case DUPLICATE_CURRENT ->
                    LocationObservationIngestionResult
                            .DUPLICATE_CURRENT;

            case STALE_SEQUENCE ->
                    LocationObservationIngestionResult
                            .STALE_SEQUENCE;

            case NO_ACTIVE_SESSION ->
                    LocationObservationIngestionResult
                            .NO_ACTIVE_SESSION;

            case SESSION_MISMATCH ->
                    LocationObservationIngestionResult
                            .SESSION_MISMATCH;

            case USER_MISMATCH ->
                    LocationObservationIngestionResult
                            .USER_MISMATCH;

            case DEVICE_MISMATCH ->
                    LocationObservationIngestionResult
                            .DEVICE_MISMATCH;
        };
    }
}