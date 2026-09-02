package com.mobilityos.route.navigation.dto;

public record NavigationOption(
        String routeToken,
        String encodedPolyline,
        Long distanceMeters,
        Long durationSeconds,
        Long staticDurationSeconds,
        boolean recommendedByProvider
) {

    public Long trafficDelaySeconds() {

        if (durationSeconds == null
                || staticDurationSeconds == null) {
            return null;
        }

        return Math.max(
                0L,
                durationSeconds - staticDurationSeconds
        );
    }
}