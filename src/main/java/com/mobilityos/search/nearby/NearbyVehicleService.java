package com.mobilityos.search.nearby;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.freshness.FreshnessCalculator;
import com.mobilityos.search.dto.NearbyVehicleResponse;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NearbyVehicleService {

    private final LiveLocationCache liveLocationCache;
    private final VehicleRepository vehicleRepository;
    private final FreshnessCalculator freshnessCalculator;

    public NearbyVehicleService(
            LiveLocationCache liveLocationCache,
            VehicleRepository vehicleRepository,
            FreshnessCalculator freshnessCalculator
    ) {
        this.liveLocationCache = liveLocationCache;
        this.vehicleRepository = vehicleRepository;
        this.freshnessCalculator = freshnessCalculator;
    }

    public List<NearbyVehicleResponse> findNearby(
            double latitude,
            double longitude,
            double radiusMeters
    ) {
        /*
         * Redis GEOSEARCH gives us geographically nearby vehicles
         * already ordered nearest-first.
         */
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                liveLocationCache.searchNearby(
                        latitude,
                        longitude,
                        radiusMeters
                );

        List<RedisCandidate> redisCandidates =
                new ArrayList<>();

        Set<Long> redisVehicleIds =
                new LinkedHashSet<>();

        /*
         * Capture Redis ordering before doing any database lookup.
         *
         * findAllById() does not guarantee result ordering.
         */
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result
                : results) {

            Long vehicleId =
                    Long.valueOf(
                            result.getContent().getName()
                    );

            redisCandidates.add(
                    new RedisCandidate(
                            vehicleId,
                            result.getContent().getPoint(),
                            result.getDistance().getValue()
                    )
            );

            redisVehicleIds.add(vehicleId);
        }

        /*
         * One Redis HMGET instead of one freshness lookup
         * for every vehicle.
         */
        Map<Long, Instant> lastPingByVehicleId =
                liveLocationCache.getLastPingTimes(
                        redisVehicleIds
                );

        List<NearbyCandidate> candidates =
                new ArrayList<>();

        Set<Long> candidateVehicleIds =
                new LinkedHashSet<>();

        /*
         * Remove candidates with no freshness information
         * and vehicles that have become OFFLINE.
         *
         * LIVE and STALE vehicles remain visible.
         */
        for (RedisCandidate candidate : redisCandidates) {

            Instant lastPing =
                    lastPingByVehicleId.get(
                            candidate.vehicleId()
                    );

            if (lastPing == null) {
                continue;
            }

            FreshnessCalculator.Freshness freshness =
                    freshnessCalculator.compute(
                            lastPing
                    );

            if (freshness
                    == FreshnessCalculator.Freshness.OFFLINE) {
                continue;
            }

            candidates.add(
                    new NearbyCandidate(
                            candidate.vehicleId(),
                            candidate.point(),
                            candidate.distanceMeters(),
                            freshness
                    )
            );

            candidateVehicleIds.add(
                    candidate.vehicleId()
            );
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        /*
         * One database query for all surviving candidates.
         *
         * Redis may contain old vehicle IDs, so the database
         * remains the source of truth for vehicle existence
         * and ACTIVE lifecycle state.
         */
        Map<Long, Vehicle> activeVehiclesById =
                vehicleRepository
                        .findAllById(candidateVehicleIds)
                        .stream()
                        .filter(Vehicle::isActive)
                        .collect(
                                Collectors.toMap(
                                        Vehicle::getId,
                                        Function.identity()
                                )
                        );

        List<NearbyVehicleResponse> nearby =
                new ArrayList<>();

        /*
         * Iterate the original candidate order so the API
         * preserves Redis's nearest-first ordering.
         */
        for (NearbyCandidate candidate : candidates) {

            Vehicle vehicle =
                    activeVehiclesById.get(
                            candidate.vehicleId()
                    );

            if (vehicle == null) {
                continue;
            }

            nearby.add(
                    new NearbyVehicleResponse(
                            candidate.vehicleId(),
                            vehicle.getPlateNumber(),
                            vehicle.getVehicleType().name(),
                            candidate.point().getY(),
                            candidate.point().getX(),
                            candidate.distanceMeters(),
                            candidate.freshness().name()
                    )
            );
        }

        return nearby;
    }

    private record RedisCandidate(
            Long vehicleId,
            Point point,
            Double distanceMeters
    ) {
    }

    private record NearbyCandidate(
            Long vehicleId,
            Point point,
            Double distanceMeters,
            FreshnessCalculator.Freshness freshness
    ) {
    }
}