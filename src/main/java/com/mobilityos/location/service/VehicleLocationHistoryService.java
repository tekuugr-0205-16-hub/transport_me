package com.mobilityos.location.service;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.dto.GpsPingRequest;
import com.mobilityos.location.entity.VehicleLocationHistory;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.quality.LocationQualityPolicy;
import com.mobilityos.location.repository.VehicleLocationHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class VehicleLocationHistoryService {

    private static final double MIN_DISTANCE_METERS =
            15.0;

    private static final Duration MIN_SAVE_INTERVAL =
            Duration.ofSeconds(5);

    private static final Duration FORCE_SAVE_INTERVAL =
            Duration.ofSeconds(60);

    private static final double EARTH_RADIUS_METERS =
            6_371_000.0;

    private final VehicleLocationHistoryRepository repository;
    private final LocationQualityPolicy locationQualityPolicy;

    public VehicleLocationHistoryService(
            VehicleLocationHistoryRepository repository,
            LocationQualityPolicy locationQualityPolicy
    ) {
        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null"
                );

        this.locationQualityPolicy =
                Objects.requireNonNull(
                        locationQualityPolicy,
                        "locationQualityPolicy must not be null"
                );
    }

    /*
     * Legacy synchronous GPS path.
     *
     * Keep until the old endpoint is retired.
     *
     * Legacy history rows intentionally have no canonical
     * observation/session/sequence identity.
     */
    @Transactional
    public boolean recordIfUseful(
            Vehicle vehicle,
            User submittedByUser,
            GpsPingRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Instant now =
                Instant.now();

        if (!locationQualityPolicy.isAcceptable(
                request,
                now
        )) {
            return false;
        }

        Instant recordedAt =
                request.recordedAt() != null
                        ? request.recordedAt()
                        : now;

        return recordIfUsefulInternal(
                vehicle,
                submittedByUser,
                null,
                null,
                null,
                request.latitude(),
                request.longitude(),
                request.speed(),
                request.heading(),
                request.accuracyMeters(),
                recordedAt,
                now
        );
    }

    /*
     * Canonical asynchronous GPS path.
     *
     * Quality was already checked before publication,
     * but checking again here gives us a defensive
     * boundary around durable history.
     *
     * Use observation.receivedAt() as the reference time.
     * Do NOT use Instant.now(), because a Redis event may
     * be processed seconds or minutes after ingestion.
     *
     * Canonical identity is preserved into PostgreSQL:
     *
     * - observationId
     * - trackingSessionId
     * - sequenceNumber
     */
    @Transactional
    public boolean recordIfUseful(
            Vehicle vehicle,
            User submittedByUser,
            LocationObservation observation
    ) {
        Objects.requireNonNull(
                observation,
                "observation must not be null"
        );

        if (!locationQualityPolicy.isAcceptable(
                observation,
                observation.receivedAt()
        )) {
            return false;
        }

        return recordIfUsefulInternal(
                vehicle,
                submittedByUser,
                observation.observationId(),
                observation.trackingSessionId(),
                observation.sequenceNumber(),
                observation.latitude(),
                observation.longitude(),
                observation.speedMetersPerSecond(),
                observation.headingDegrees(),
                observation.accuracyMeters(),
                observation.recordedAt(),
                observation.receivedAt()
        );
    }

    private boolean recordIfUsefulInternal(
            Vehicle vehicle,
            User submittedByUser,
            UUID observationId,
            UUID trackingSessionId,
            Long sequenceNumber,
            double latitude,
            double longitude,
            Double speed,
            Double heading,
            Double accuracyMeters,
            Instant recordedAt,
            Instant receivedAt
    ) {
        Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );

        Objects.requireNonNull(
                submittedByUser,
                "submittedByUser must not be null"
        );

        Objects.requireNonNull(
                recordedAt,
                "recordedAt must not be null"
        );

        Objects.requireNonNull(
                receivedAt,
                "receivedAt must not be null"
        );

        Optional<VehicleLocationHistory> latestOptional =
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                vehicle.getId()
                        );

        if (latestOptional.isPresent()) {

            VehicleLocationHistory latest =
                    latestOptional.get();

            /*
             * Duplicate or out-of-order observations
             * must never become historical points.
             */
            if (!recordedAt.isAfter(
                    latest.getRecordedAt()
            )) {
                return false;
            }

            Duration elapsed =
                    Duration.between(
                            latest.getRecordedAt(),
                            recordedAt
                    );

            /*
             * Preserve the existing minimum interval.
             */
            if (elapsed.compareTo(
                    MIN_SAVE_INTERVAL
            ) < 0) {
                return false;
            }

            double distanceMeters =
                    distanceMeters(
                            latest.getLatitude(),
                            latest.getLongitude(),
                            latitude,
                            longitude
                    );

            /*
             * Save meaningful movement.
             */
            if (distanceMeters
                    >= MIN_DISTANCE_METERS) {

                save(
                        vehicle,
                        submittedByUser,
                        observationId,
                        trackingSessionId,
                        sequenceNumber,
                        latitude,
                        longitude,
                        speed,
                        heading,
                        accuracyMeters,
                        recordedAt,
                        receivedAt
                );

                return true;
            }

            /*
             * Save occasional proof of presence even when
             * the vehicle has barely moved.
             */
            if (elapsed.compareTo(
                    FORCE_SAVE_INTERVAL
            ) >= 0) {

                save(
                        vehicle,
                        submittedByUser,
                        observationId,
                        trackingSessionId,
                        sequenceNumber,
                        latitude,
                        longitude,
                        speed,
                        heading,
                        accuracyMeters,
                        recordedAt,
                        receivedAt
                );

                return true;
            }

            return false;
        }

        /*
         * First useful historical observation.
         */
        save(
                vehicle,
                submittedByUser,
                observationId,
                trackingSessionId,
                sequenceNumber,
                latitude,
                longitude,
                speed,
                heading,
                accuracyMeters,
                recordedAt,
                receivedAt
        );

        return true;
    }

    private void save(
            Vehicle vehicle,
            User submittedByUser,
            UUID observationId,
            UUID trackingSessionId,
            Long sequenceNumber,
            double latitude,
            double longitude,
            Double speed,
            Double heading,
            Double accuracyMeters,
            Instant recordedAt,
            Instant receivedAt
    ) {
        VehicleLocationHistory location =
                new VehicleLocationHistory(
                        vehicle,
                        submittedByUser,
                        observationId,
                        trackingSessionId,
                        sequenceNumber,
                        latitude,
                        longitude,
                        speed,
                        heading,
                        accuracyMeters,
                        recordedAt,
                        receivedAt
                );

        repository.save(
                location
        );
    }

    private double distanceMeters(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {
        double latitudeRadians1 =
                Math.toRadians(
                        latitude1
                );

        double latitudeRadians2 =
                Math.toRadians(
                        latitude2
                );

        double latitudeDifference =
                Math.toRadians(
                        latitude2 - latitude1
                );

        double longitudeDifference =
                Math.toRadians(
                        longitude2 - longitude1
                );

        double a =
                Math.sin(latitudeDifference / 2.0)
                        * Math.sin(latitudeDifference / 2.0)
                        +
                        Math.cos(latitudeRadians1)
                                * Math.cos(latitudeRadians2)
                                * Math.sin(
                                longitudeDifference / 2.0
                        )
                                * Math.sin(
                                longitudeDifference / 2.0
                        );

        double c =
                2.0 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1.0 - a)
                );

        return EARTH_RADIUS_METERS * c;
    }
}