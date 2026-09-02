package com.mobilityos.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartVehicleRouteRequest(

        @NotBlank
        @Size(max = 255)
        String originName,

        @NotBlank
        @Size(max = 255)
        String destinationName

) {
}