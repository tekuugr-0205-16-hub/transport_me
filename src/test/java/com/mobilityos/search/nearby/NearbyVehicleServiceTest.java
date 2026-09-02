package com.mobilityos.search.nearby;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.freshness.FreshnessCalculator;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.search.dto.NearbyVehicleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NearbyVehicleServiceTest {

    @Test
    void batchesRedisFreshnessAndDatabaseLoadingWhilePreservingDistanceOrder()
            throws Exception {

        LiveLocationCache liveLocationCache =
                mock(LiveLocationCache.class);

        VehicleRepository vehicleRepository =
                mock(VehicleRepository.class);

        FreshnessCalculator freshnessCalculator =
                mock(FreshnessCalculator.class);

        NearbyVehicleService service =
                new NearbyVehicleService(
                        liveLocationCache,
                        vehicleRepository,
                        freshnessCalculator
                );

        GeoResult<RedisGeoCommands.GeoLocation<String>> first =
                geoResult(
                        "10",
                        38.7500,
                        9.0300,
                        100.0
                );

        GeoResult<RedisGeoCommands.GeoLocation<String>> second =
                geoResult(
                        "20",
                        38.7600,
                        9.0400,
                        250.0
                );

        when(liveLocationCache.searchNearby(
                9.0,
                38.7,
                1000.0
        )).thenReturn(
                new GeoResults<>(
                        List.of(first, second)
                )
        );

        Instant now = Instant.now();

        when(liveLocationCache.getLastPingTimes(any()))
                .thenReturn(
                        Map.of(
                                10L, now,
                                20L, now
                        )
                );

        when(freshnessCalculator.compute(any(Instant.class)))
                .thenReturn(
                        FreshnessCalculator.Freshness.LIVE
                );

        Vehicle fartherVehicle =
                vehicle(
                        20L,
                        "AA-20",
                        Vehicle.VehicleType.BUS
                );

        Vehicle nearerVehicle =
                vehicle(
                        10L,
                        "AA-10",
                        Vehicle.VehicleType.MINIBUS
                );

        /*
         * Deliberately return DB rows in the opposite order
         * to prove Redis nearest-first ordering is preserved.
         */
        when(vehicleRepository.findAllById(any()))
                .thenReturn(
                        List.of(
                                fartherVehicle,
                                nearerVehicle
                        )
                );

        List<NearbyVehicleResponse> result =
                service.findNearby(
                        9.0,
                        38.7,
                        1000.0
                );

        assertEquals(
                2,
                result.size()
        );

        assertEquals(
                10L,
                result.get(0).vehicleId()
        );

        assertEquals(
                20L,
                result.get(1).vehicleId()
        );

        /*
         * Also verify the correct vehicle data survived
         * the reordered database result.
         */
        assertEquals(
                "AA-10",
                result.get(0).plateNumber()
        );

        assertEquals(
                "AA-20",
                result.get(1).plateNumber()
        );

        verify(
                liveLocationCache,
                times(1)
        ).getLastPingTimes(any());

        verify(
                liveLocationCache,
                never()
        ).getLastPingTime(any());

        verify(vehicleRepository)
                .findAllById(any());

        verify(
                vehicleRepository,
                never()
        ).findById(any());
    }

    private static GeoResult<RedisGeoCommands.GeoLocation<String>>
    geoResult(
            String vehicleId,
            double longitude,
            double latitude,
            double distanceMeters
    ) {

        RedisGeoCommands.GeoLocation<String> location =
                new RedisGeoCommands.GeoLocation<>(
                        vehicleId,
                        new Point(
                                longitude,
                                latitude
                        )
                );

        return new GeoResult<>(
                location,
                new Distance(
                        distanceMeters,
                        Metrics.NEUTRAL
                )
        );
    }

    private static Vehicle vehicle(
            Long id,
            String plate,
            Vehicle.VehicleType type
    ) throws Exception {

        Organization organization =
                new Organization(
                        "Test Transport",
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        plate,
                        type,
                        12
                );

        Field idField =
                Vehicle.class.getDeclaredField("id");

        idField.setAccessible(true);
        idField.set(vehicle, id);

        return vehicle;
    }
}