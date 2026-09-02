package com.mobilityos.location.service;

import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.dto.LiveLocationResponse;
import com.mobilityos.location.freshness.FreshnessCalculator;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class VehicleTrackingService {

    private final VehicleRepository vehicleRepository;
    private final LiveLocationCache liveLocationCache;
    private final FreshnessCalculator freshnessCalculator;

    public VehicleTrackingService(
            VehicleRepository vehicleRepository,
            LiveLocationCache liveLocationCache,
            FreshnessCalculator freshnessCalculator
    ) {
        this.vehicleRepository = vehicleRepository;
        this.liveLocationCache = liveLocationCache;
        this.freshnessCalculator = freshnessCalculator;
    }

    public LiveLocationResponse getLiveLocation(
            Long vehicleId
    ) {
        Vehicle vehicle =
                vehicleRepository.findById(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle not found"
                                )
                        );

        /*
         * Inactive vehicles are not publicly trackable.
         *
         * Return 404 rather than exposing the existence/state
         * of a vehicle that is no longer active.
         */
        if (!vehicle.isActive()) {
            throw new ResourceNotFoundException(
                    "Live location is not available for this vehicle"
            );
        }

        Point position =
                liveLocationCache.getPosition(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No location data for this vehicle yet"
                                )
                        );

        Instant lastPing =
                liveLocationCache.getLastPingTime(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No location data for this vehicle yet"
                                )
                        );

        Map<Object, Object> metadata =
                liveLocationCache.getMetadata(vehicleId);

        Double speed =
                parseNullable(
                        metadata.get("speed")
                );

        Double heading =
                parseNullable(
                        metadata.get("heading")
                );

        return new LiveLocationResponse(
                vehicleId,
                position.getY(),
                position.getX(),
                speed,
                heading,
                freshnessCalculator.compute(lastPing)
        );
    }

    private Double parseNullable(
            Object value
    ) {
        if (value == null
                || value.toString().isBlank()) {
            return null;
        }

        return Double.parseDouble(
                value.toString()
        );
    }
}