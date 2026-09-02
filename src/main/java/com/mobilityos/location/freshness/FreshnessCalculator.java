package com.mobilityos.location.freshness;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class FreshnessCalculator {

    private static final Duration LIVE_THRESHOLD =
            Duration.ofSeconds(30);

    private static final Duration STALE_THRESHOLD =
            Duration.ofMinutes(2);

    public Freshness compute(
            Instant lastPing
    ) {
        Duration age =
                Duration.between(
                        lastPing,
                        Instant.now()
                );

        if (age.compareTo(LIVE_THRESHOLD) <= 0) {
            return Freshness.LIVE;
        }

        if (age.compareTo(STALE_THRESHOLD) <= 0) {
            return Freshness.STALE;
        }

        return Freshness.OFFLINE;
    }

    public enum Freshness {
        LIVE,
        STALE,
        OFFLINE
    }
}