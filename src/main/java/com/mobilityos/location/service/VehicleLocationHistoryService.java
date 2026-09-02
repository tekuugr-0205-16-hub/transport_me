package com.mobilityos.location.service;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.dto.GpsPingRequest;
import com.mobilityos.location.entity.VehicleLocationHistory;
import com.mobilityos.location.quality.LocationQualityPolicy;
import com.mobilityos.location.repository.VehicleLocationHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class VehicleLocationHistoryService {

    private static final double MIN_DISTANCE_METERS = 15.0;

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
        this.repository = repository;
        this.locationQualityPolicy = locationQualityPolicy;
    }

    @Transactional
    public boolean recordIfUseful(
            Vehicle vehicle,
            User submittedByUser,
            GpsPingRequest request
    ) {
        Instant now = Instant.now();

        /*
         * One centralized definition of whether the
         * GPS observation is trustworthy.
         */
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

        Optional<VehicleLocationHistory> latestOptional =
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                vehicle.getId()
                        );

        if (latestOptional.isPresent()) {

            VehicleLocationHistory latest =
                    latestOptional.get();

            /*
             * Do not write duplicate or out-of-order
             * points through this endpoint.
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
             * Protect PostgreSQL from excessive writes.
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
                            request.latitude(),
                            request.longitude()
                    );

            /*
             * Vehicle moved enough for the point to be
             * meaningful historical movement.
             */
            if (distanceMeters >= MIN_DISTANCE_METERS) {

                save(
                        vehicle,
                        submittedByUser,
                        request,
                        recordedAt
                );

                return true;
            }

            /*
             * Even when the vehicle stays in one place,
             * periodically save proof that it was still
             * reporting location.
             */
            if (elapsed.compareTo(
                    FORCE_SAVE_INTERVAL
            ) >= 0) {

                save(
                        vehicle,
                        submittedByUser,
                        request,
                        recordedAt
                );

                return true;
            }

            return false;
        }

        /*
         * First useful historical position.
         */
        save(
                vehicle,
                submittedByUser,
                request,
                recordedAt
        );

        return true;
    }

    private void save(
            Vehicle vehicle,
            User submittedByUser,
            GpsPingRequest request,
            Instant recordedAt
    ) {
        VehicleLocationHistory location =
                new VehicleLocationHistory(
                        vehicle,
                        submittedByUser,
                        request.latitude(),
                        request.longitude(),
                        request.speed(),
                        request.heading(),
                        request.accuracyMeters(),
                        recordedAt
                );

        repository.save(location);
    }

    private double distanceMeters(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {
        double latitudeRadians1 =
                Math.toRadians(latitude1);

        double latitudeRadians2 =
                Math.toRadians(latitude2);

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