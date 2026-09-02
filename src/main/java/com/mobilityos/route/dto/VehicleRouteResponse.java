package com.mobilityos.route.dto;

import com.mobilityos.route.entity.VehicleRoute;

import java.time.Instant;

public record VehicleRouteResponse(
        Long id,
        Long vehicleId,
        Long setByUserId,
        String originName,
        String destinationName,
        VehicleRoute.RouteStatus status,
        Instant startedAt,
        Instant endedAt
) {

    public static VehicleRouteResponse from(
            VehicleRoute route
    ) {
        return new VehicleRouteResponse(
                route.getId(),
                route.getVehicle().getId(),
                route.getSetByUser().getId(),
                route.getOriginName(),
                route.getDestinationName(),
                route.getStatus(),
                route.getStartedAt(),
                route.getEndedAt()
        );
    }
}