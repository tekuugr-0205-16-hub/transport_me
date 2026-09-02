package com.mobilityos.route.navigation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NavigationOptionsRequest(

        @NotBlank
        @Size(max = 255)
        String destinationName,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double destinationLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double destinationLongitude

) {
}