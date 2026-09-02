package com.mobilityos.route.navigation.dto;

public record NavigationRequest(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude,
        RoutingPreference routingPreference,
        boolean requestAlternatives
) {

    public NavigationRequest {
        validateLatitude(
                originLatitude,
                "origin latitude"
        );

        validateLongitude(
                originLongitude,
                "origin longitude"
        );

        validateLatitude(
                destinationLatitude,
                "destination latitude"
        );

        validateLongitude(
                destinationLongitude,
                "destination longitude"
        );

        if (routingPreference == null) {
            throw new IllegalArgumentException(
                    "routing preference must not be null"
            );
        }
    }

    private static void validateLatitude(
            double latitude,
            String fieldName
    ) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between -90 and 90"
            );
        }
    }

    private static void validateLongitude(
            double longitude,
            String fieldName
    ) {
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between -180 and 180"
            );
        }
    }

    public enum RoutingPreference {

        /**
         * Uses live traffic while keeping route calculation
         * reasonably fast.
         */
        TRAFFIC_AWARE,

        /**
         * Performs a more comprehensive traffic-aware search.
         * Useful when the driver explicitly asks for the best
         * available route alternatives.
         */
        TRAFFIC_AWARE_OPTIMAL
    }
}