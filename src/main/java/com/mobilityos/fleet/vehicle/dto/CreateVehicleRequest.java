package com.mobilityos.fleet.vehicle.dto;

import com.mobilityos.fleet.vehicle.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(

        @NotBlank
        @Size(max = 20)
        String plateNumber,

        @NotNull
        Vehicle.VehicleType vehicleType,

        @NotNull
        @Positive
        Integer capacity

) {}