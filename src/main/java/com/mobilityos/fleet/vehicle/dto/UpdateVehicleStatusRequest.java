package com.mobilityos.fleet.vehicle.dto;

import com.mobilityos.fleet.vehicle.Vehicle;
import jakarta.validation.constraints.NotNull;

public record UpdateVehicleStatusRequest(

        @NotNull
        Vehicle.VehicleStatus status

) {
}