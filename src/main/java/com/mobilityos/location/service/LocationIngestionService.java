package com.mobilityos.location.service;

import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.dto.GpsPingRequest;
import com.mobilityos.location.dto.LiveLocationResponse;
import com.mobilityos.location.freshness.FreshnessCalculator;
import com.mobilityos.location.quality.LocationQualityPolicy;
import com.mobilityos.realtime.publisher.RealtimePublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LocationIngestionService {

    private final VehicleRepository vehicleRepository;
    private final FleetAccessService fleetAccessService;
    private final UserRepository userRepository;

    private final LiveLocationCache liveLocationCache;
    private final VehicleLocationHistoryService vehicleLocationHistoryService;
    private final LocationQualityPolicy locationQualityPolicy;

    private final RealtimePublisher realtimePublisher;
    private final FreshnessCalculator freshnessCalculator;

    public LocationIngestionService(
            VehicleRepository vehicleRepository,
            FleetAccessService fleetAccessService,
            UserRepository userRepository,
            LiveLocationCache liveLocationCache,
            VehicleLocationHistoryService vehicleLocationHistoryService,
            LocationQualityPolicy locationQualityPolicy,
            RealtimePublisher realtimePublisher,
            FreshnessCalculator freshnessCalculator
    ) {
        this.vehicleRepository = vehicleRepository;
        this.fleetAccessService = fleetAccessService;
        this.userRepository = userRepository;
        this.liveLocationCache = liveLocationCache;
        this.vehicleLocationHistoryService =
                vehicleLocationHistoryService;
        this.locationQualityPolicy = locationQualityPolicy;
        this.realtimePublisher = realtimePublisher;
        this.freshnessCalculator = freshnessCalculator;
    }

    public void ingestPing(
            Long currentUserId,
            Long vehicleId,
            GpsPingRequest request
    ) {
        /*
         * Vehicle must exist.
         */
        var vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Vehicle not found"
                        )
                );

        /*
         * Only a VehicleMember may submit operational GPS.
         *
         * Organization OWNER/ADMIN status alone is not
         * operational permission.
         */
        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        /*
         * Vehicle must currently be operational.
         */
        if (!vehicle.isActive()) {
            throw new ForbiddenException(
                    "Only ACTIVE vehicles can send live location"
            );
        }

        Instant now = Instant.now();

        /*
         * A poor-quality GPS observation must not corrupt
         * live Redis state, realtime subscribers, or
         * historical analytics.
         *
         * We intentionally ignore such observations.
         */
        if (!locationQualityPolicy.isAcceptable(
                request,
                now
        )) {
            return;
        }

        /*
         * Resolve the authenticated user only after the
         * point has passed the quality gate.
         */
        var submittedByUser =
                userRepository.findById(currentUserId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found"
                                )
                        );

        /*
         * PostgreSQL keeps useful historical points.
         *
         * The history service applies additional rules such
         * as minimum time and minimum movement distance.
         */
        vehicleLocationHistoryService.recordIfUseful(
                vehicle,
                submittedByUser,
                request
        );

        /*
         * Redis represents the latest trustworthy live
         * vehicle position.
         */
        liveLocationCache.updateLocation(
                vehicleId,
                request.latitude(),
                request.longitude(),
                request.speed(),
                request.heading()
        );

        /*
         * Realtime consumers receive only trustworthy
         * accepted live observations.
         */
        LiveLocationResponse update =
                new LiveLocationResponse(
                        vehicleId,
                        request.latitude(),
                        request.longitude(),
                        request.speed(),
                        request.heading(),
                        freshnessCalculator.compute(
                                Instant.now()
                        )
                );

        realtimePublisher.publishLocationUpdate(
                vehicleId,
                update
        );
    }
}