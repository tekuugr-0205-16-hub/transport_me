package com.mobilityos.fleet.vehicle.dto;

import com.mobilityos.fleet.vehicle.Vehicle;

public record VehicleResponse(
        Long id,
        Long organizationId,
        String plateNumber,
        Vehicle.VehicleType vehicleType,
        Integer capacity,
        Vehicle.VehicleStatus status
) {

    public static VehicleResponse from(
            Vehicle vehicle
    ) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getOperator().getId(),
                vehicle.getPlateNumber(),
                vehicle.getVehicleType(),
                vehicle.getCapacity(),
                vehicle.getStatus()
        );
    }
}