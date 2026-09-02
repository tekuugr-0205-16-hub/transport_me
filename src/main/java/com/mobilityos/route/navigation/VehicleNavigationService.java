package com.mobilityos.route.navigation;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.freshness.FreshnessCalculator;
import com.mobilityos.route.navigation.dto.NavigationOption;
import com.mobilityos.route.navigation.dto.NavigationOptionsRequest;
import com.mobilityos.route.navigation.dto.NavigationRequest;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class VehicleNavigationService {

    private final VehicleRepository vehicleRepository;
    private final FleetAccessService fleetAccessService;
    private final LiveLocationCache liveLocationCache;
    private final FreshnessCalculator freshnessCalculator;
    private final NavigationProvider navigationProvider;

    public VehicleNavigationService(
            VehicleRepository vehicleRepository,
            FleetAccessService fleetAccessService,
            LiveLocationCache liveLocationCache,
            FreshnessCalculator freshnessCalculator,
            NavigationProvider navigationProvider
    ) {
        this.vehicleRepository = vehicleRepository;
        this.fleetAccessService = fleetAccessService;
        this.liveLocationCache = liveLocationCache;
        this.freshnessCalculator = freshnessCalculator;
        this.navigationProvider = navigationProvider;
    }

    // =========================================================
    // GET NAVIGATION OPTIONS
    // =========================================================

    public List<NavigationOption> getNavigationOptions(
            Long currentUserId,
            Long vehicleId,
            NavigationOptionsRequest request
    ) {
        Vehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle not found"
                                )
                        );

        /*
         * Navigation is an operational action.
         *
         * Organization OWNER / ADMIN may observe the vehicle,
         * but only a VehicleMember may request navigation
         * options for the vehicle.
         */
        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        if (!vehicle.isActive()) {
            throw new ForbiddenException(
                    "Only ACTIVE vehicles can use navigation"
            );
        }

        Instant lastPing =
                liveLocationCache
                        .getLastPingTime(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle has no live location"
                                )
                        );

        FreshnessCalculator.Freshness freshness =
                freshnessCalculator.compute(
                        lastPing
                );

        /*
         * Navigation should start from a genuinely current
         * position. STALE / OFFLINE coordinates may produce
         * a route beginning from the wrong place.
         */
        if (freshness
                != FreshnessCalculator.Freshness.LIVE) {

            throw new ConflictException(
                    "Vehicle location is not fresh enough for navigation"
            );
        }

        Point currentPosition =
                liveLocationCache
                        .getPosition(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle position is unavailable"
                                )
                        );

        /*
         * Redis/Spring Point:
         *
         * X = longitude
         * Y = latitude
         */
        double originLongitude =
                currentPosition.getX();

        double originLatitude =
                currentPosition.getY();

        NavigationRequest navigationRequest =
                new NavigationRequest(
                        originLatitude,
                        originLongitude,
                        request.destinationLatitude(),
                        request.destinationLongitude(),
                        NavigationRequest.RoutingPreference.TRAFFIC_AWARE,
                        true
                );

        List<NavigationOption> options =
                navigationProvider.calculateRoutes(
                        navigationRequest
                );

        if (options == null || options.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No navigation routes were found"
            );
        }

        return options;
    }
}