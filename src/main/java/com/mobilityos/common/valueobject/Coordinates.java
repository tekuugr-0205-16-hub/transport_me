package com.mobilityos.common.valueobject;

import java.util.Objects;

public record Coordinates(
        double latitude,
        double longitude
) {

    public Coordinates {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180"
            );
        }
    }

    public static Coordinates of(
            double latitude,
            double longitude
    ) {
        return new Coordinates(latitude, longitude);
    }
}
