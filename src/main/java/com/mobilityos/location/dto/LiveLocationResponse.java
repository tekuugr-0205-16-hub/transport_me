
package com.mobilityos.location.dto;

import com.mobilityos.location.freshness.FreshnessCalculator;

public record LiveLocationResponse(
        Long vehicleId,
        Double latitude,
        Double longitude,
        Double speed,
        Double heading,
        FreshnessCalculator.Freshness freshness
) {}