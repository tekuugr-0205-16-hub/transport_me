
package com.mobilityos.location.cache;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps Redis geospatial + hash operations for live vehicle positions.
 * GEOADD holds the queryable lat/lng; a parallel hash holds metadata
 * (timestamp, speed, heading) needed to compute freshness and display
 * state, since GEO commands alone can't carry extra fields.
 */
@Component
public class LiveLocationCache {

    private static final String GEO_KEY = "vehicles:live:geo";
    private static final String META_KEY_PREFIX = "vehicle:live:meta:";
    private static final String LAST_PING_KEY = "vehicles:live:last-ping";

    private final RedisTemplate<String, String> redisTemplate;

    public LiveLocationCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateLocation(Long vehicleId, double latitude, double longitude,
                               Double speed, Double heading) {
        String member = String.valueOf(vehicleId);

        redisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), member);

        String timestamp = Instant.now().toString();
        String metaKey = META_KEY_PREFIX + vehicleId;
        redisTemplate.opsForHash().putAll(metaKey, Map.of(
                "timestamp", timestamp,
                "speed", speed != null ? String.valueOf(speed) : "",
                "heading", heading != null ? String.valueOf(heading) : ""
        ));

        // Search needs freshness for many vehicles at once. Keeping last-ping timestamps
        // in one Redis hash lets nearby search use one HMGET instead of one HGET per
        // vehicle. The per-vehicle metadata hash remains the detailed record.
        redisTemplate.opsForHash().put(LAST_PING_KEY, member, timestamp);
    }

    public Optional<Instant> getLastPingTime(Long vehicleId) {
        Object timestamp = redisTemplate.opsForHash().get(LAST_PING_KEY, String.valueOf(vehicleId));

        // Backward-compatible fallback for locations cached before the shared
        // freshness index was introduced. Fresh pings populate LAST_PING_KEY.
        if (timestamp == null) {
            timestamp = redisTemplate.opsForHash().get(META_KEY_PREFIX + vehicleId, "timestamp");
        }
        if (timestamp == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse((String) timestamp));
    }

    /**
     * Fetches freshness timestamps for many vehicles in a single Redis HMGET.
     * Returned map contains only vehicles that currently have a timestamp.
     */
    public Map<Long, Instant> getLastPingTimes(Collection<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return Map.of();
        }

        List<Long> orderedIds = List.copyOf(vehicleIds);
        List<String> fields = orderedIds.stream()
                .map(String::valueOf)
                .toList();

        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        List<String> timestamps = hashOperations.multiGet(LAST_PING_KEY, fields);

        Map<Long, Instant> lastPingByVehicleId = new LinkedHashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            String timestamp = timestamps.get(i);
            if (timestamp != null && !timestamp.isBlank()) {
                lastPingByVehicleId.put(orderedIds.get(i), Instant.parse(timestamp));
            }
        }
        return lastPingByVehicleId;
    }

    public Optional<Point> getPosition(Long vehicleId) {
        var results = redisTemplate.opsForGeo().position(GEO_KEY, String.valueOf(vehicleId));
        if (results == null || results.isEmpty() || results.get(0) == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    public Map<Object, Object> getMetadata(Long vehicleId) {
        return redisTemplate.opsForHash().entries(META_KEY_PREFIX + vehicleId);
    }

    public GeoResults<RedisGeoCommands.GeoLocation<String>> searchNearby(double latitude, double longitude, double radiusMeters) {
        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending();

        return redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radiusMeters, Metrics.NEUTRAL),
                args
        );
    }
}