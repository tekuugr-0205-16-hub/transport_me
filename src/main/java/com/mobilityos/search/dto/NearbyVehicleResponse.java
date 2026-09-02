
package com.mobilityos.search.dto;

public record NearbyVehicleResponse(
        Long vehicleId,
        String plateNumber,
        String vehicleType,
        Double latitude,
        Double longitude,
        Double distanceMeters,
        String freshness
) {}