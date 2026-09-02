package com.mobilityos.route.navigation;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.location.cache.LiveLocationCache;
import com.mobilityos.location.freshness.FreshnessCalculator;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.route.navigation.dto.NavigationOption;
import com.mobilityos.route.navigation.dto.NavigationOptionsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VehicleNavigationServiceTest {

    @Test
    void returnsNavigationOptionsUsingLiveVehiclePosition()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        Instant now = Instant.now();

        when(fixture.liveLocationCache.getLastPingTime(10L))
                .thenReturn(Optional.of(now));

        when(fixture.freshnessCalculator.compute(now))
                .thenReturn(FreshnessCalculator.Freshness.LIVE);

        when(fixture.liveLocationCache.getPosition(10L))
                .thenReturn(
                        Optional.of(
                                new Point(
                                        38.7400,
                                        9.0300
                                )
                        )
                );

        NavigationOption option =
                new NavigationOption(
                        "route-token",
                        "encoded-polyline",
                        7200L,
                        1200L,
                        900L,
                        true
                );

        when(fixture.navigationProvider
                .calculateRoutes(any()))
                .thenReturn(List.of(option));

        List<NavigationOption> result =
                fixture.service.getNavigationOptions(
                        1L,
                        10L,
                        new NavigationOptionsRequest(
                                "Piassa",
                                9.0367,
                                38.7525
                        )
                );

        assertEquals(1, result.size());
        assertEquals(
                "route-token",
                result.getFirst().routeToken()
        );

        verify(fixture.fleetAccessService)
                .requireVehicleMember(
                        1L,
                        10L
                );

        verify(fixture.navigationProvider)
                .calculateRoutes(any());
    }

    @Test
    void rejectsNavigationForInactiveVehicle()
            throws Exception {

        Fixture fixture = fixture();

        fixture.vehicle.setStatus(
                Vehicle.VehicleStatus.INACTIVE
        );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        assertThrows(
                ForbiddenException.class,
                () -> fixture.service
                        .getNavigationOptions(
                                1L,
                                10L,
                                new NavigationOptionsRequest(
                                        "Piassa",
                                        9.0367,
                                        38.7525
                                )
                        )
        );

        verify(fixture.fleetAccessService)
                .requireVehicleMember(
                        1L,
                        10L
                );

        verify(
                fixture.navigationProvider,
                never()
        ).calculateRoutes(any());
    }

    @Test
    void rejectsNavigationWhenVehicleHasNoLiveLocation()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        when(fixture.liveLocationCache
                .getLastPingTime(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> fixture.service
                        .getNavigationOptions(
                                1L,
                                10L,
                                new NavigationOptionsRequest(
                                        "Piassa",
                                        9.0367,
                                        38.7525
                                )
                        )
        );

        verify(
                fixture.navigationProvider,
                never()
        ).calculateRoutes(any());
    }

    @Test
    void rejectsNavigationWhenLocationIsNotLive()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        Instant oldPing =
                Instant.now().minusSeconds(60);

        when(fixture.liveLocationCache
                .getLastPingTime(10L))
                .thenReturn(Optional.of(oldPing));

        when(fixture.freshnessCalculator.compute(oldPing))
                .thenReturn(
                        FreshnessCalculator.Freshness.STALE
                );

        assertThrows(
                ConflictException.class,
                () -> fixture.service
                        .getNavigationOptions(
                                1L,
                                10L,
                                new NavigationOptionsRequest(
                                        "Piassa",
                                        9.0367,
                                        38.7525
                                )
                        )
        );

        verify(
                fixture.navigationProvider,
                never()
        ).calculateRoutes(any());
    }

    @Test
    void rejectsWhenProviderReturnsNoRoutes()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        Instant now = Instant.now();

        when(fixture.liveLocationCache.getLastPingTime(10L))
                .thenReturn(Optional.of(now));

        when(fixture.freshnessCalculator.compute(now))
                .thenReturn(
                        FreshnessCalculator.Freshness.LIVE
                );

        when(fixture.liveLocationCache.getPosition(10L))
                .thenReturn(
                        Optional.of(
                                new Point(
                                        38.7400,
                                        9.0300
                                )
                        )
                );

        when(fixture.navigationProvider
                .calculateRoutes(any()))
                .thenReturn(List.of());

        assertThrows(
                ResourceNotFoundException.class,
                () -> fixture.service
                        .getNavigationOptions(
                                1L,
                                10L,
                                new NavigationOptionsRequest(
                                        "Piassa",
                                        9.0367,
                                        38.7525
                                )
                        )
        );
    }

    private static Fixture fixture()
            throws Exception {

        VehicleRepository vehicleRepository =
                mock(VehicleRepository.class);

        FleetAccessService fleetAccessService =
                mock(FleetAccessService.class);

        LiveLocationCache liveLocationCache =
                mock(LiveLocationCache.class);

        FreshnessCalculator freshnessCalculator =
                mock(FreshnessCalculator.class);

        NavigationProvider navigationProvider =
                mock(NavigationProvider.class);

        VehicleNavigationService service =
                new VehicleNavigationService(
                        vehicleRepository,
                        fleetAccessService,
                        liveLocationCache,
                        freshnessCalculator,
                        navigationProvider
                );

        Organization organization =
                new Organization(
                        "Test Transport",
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        "AA-90001",
                        Vehicle.VehicleType.MINIBUS,
                        12
                );

        setId(
                Vehicle.class,
                vehicle,
                10L
        );

        return new Fixture(
                service,
                vehicleRepository,
                fleetAccessService,
                liveLocationCache,
                freshnessCalculator,
                navigationProvider,
                vehicle
        );
    }

    private static void setId(
            Class<?> type,
            Object target,
            Long id
    ) throws Exception {

        Field field =
                type.getDeclaredField("id");

        field.setAccessible(true);
        field.set(
                target,
                id
        );
    }

    private record Fixture(
            VehicleNavigationService service,
            VehicleRepository vehicleRepository,
            FleetAccessService fleetAccessService,
            LiveLocationCache liveLocationCache,
            FreshnessCalculator freshnessCalculator,
            NavigationProvider navigationProvider,
            Vehicle vehicle
    ) {
    }
}