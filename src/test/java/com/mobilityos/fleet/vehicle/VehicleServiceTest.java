package com.mobilityos.fleet.vehicle;

import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.dto.UpdateVehicleStatusRequest;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.VehicleTrackingSessionService;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleServiceTest {

    // =========================================================
    // INACTIVE
    // =========================================================

    @Test
    void deactivatingVehicleTerminatesTrackingAuthority()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository
                .findByIdForUpdate(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        VehicleResponse response =
                fixture.service.updateVehicleStatus(
                        1L,
                        100L,
                        10L,
                        new UpdateVehicleStatusRequest(
                                Vehicle.VehicleStatus.INACTIVE
                        )
                );

        assertEquals(
                Vehicle.VehicleStatus.INACTIVE,
                response.status()
        );

        InOrder order =
                inOrder(
                        fixture.vehicleRepository,
                        fixture.trackingSessionService
                );

        order.verify(
                fixture.vehicleRepository
        ).findByIdForUpdate(
                10L
        );

        order.verify(
                fixture.trackingSessionService
        ).terminateActiveSessionForVehicle(
                10L,
                TrackingSessionEndReason.VEHICLE_DEACTIVATED
        );

        verify(
                fixture.fleetAccessService
        ).requireOrganizationManager(
                1L,
                100L
        );
    }

    // =========================================================
    // MAINTENANCE
    // =========================================================

    @Test
    void maintenanceStatusAlsoTerminatesTrackingAuthority()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository
                .findByIdForUpdate(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        VehicleResponse response =
                fixture.service.updateVehicleStatus(
                        1L,
                        100L,
                        10L,
                        new UpdateVehicleStatusRequest(
                                Vehicle.VehicleStatus.UNDER_MAINTENANCE
                        )
                );

        assertEquals(
                Vehicle.VehicleStatus.UNDER_MAINTENANCE,
                response.status()
        );

        verify(
                fixture.trackingSessionService
        ).terminateActiveSessionForVehicle(
                10L,
                TrackingSessionEndReason.VEHICLE_DEACTIVATED
        );
    }

    // =========================================================
    // REACTIVATION
    // =========================================================

    @Test
    void reactivatingVehicleDoesNotTerminateTracking()
            throws Exception {

        Fixture fixture = fixture();

        fixture.vehicle.setStatus(
                Vehicle.VehicleStatus.INACTIVE
        );

        when(fixture.vehicleRepository
                .findByIdForUpdate(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        VehicleResponse response =
                fixture.service.updateVehicleStatus(
                        1L,
                        100L,
                        10L,
                        new UpdateVehicleStatusRequest(
                                Vehicle.VehicleStatus.ACTIVE
                        )
                );

        assertEquals(
                Vehicle.VehicleStatus.ACTIVE,
                response.status()
        );

        verifyNoInteractions(
                fixture.trackingSessionService
        );
    }

    // =========================================================
    // IDEMPOTENT NON-ACTIVE SELF-HEAL
    // =========================================================

    @Test
    void repeatedInactiveStatusStillRevokesAnyStaleTrackingAuthority()
            throws Exception {

        Fixture fixture = fixture();

        fixture.vehicle.setStatus(
                Vehicle.VehicleStatus.INACTIVE
        );

        when(fixture.vehicleRepository
                .findByIdForUpdate(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        VehicleResponse response =
                fixture.service.updateVehicleStatus(
                        1L,
                        100L,
                        10L,
                        new UpdateVehicleStatusRequest(
                                Vehicle.VehicleStatus.INACTIVE
                        )
                );

        assertEquals(
                Vehicle.VehicleStatus.INACTIVE,
                response.status()
        );

        verify(
                fixture.trackingSessionService
        ).terminateActiveSessionForVehicle(
                10L,
                TrackingSessionEndReason.VEHICLE_DEACTIVATED
        );
    }

    // =========================================================
    // CROSS-ORGANIZATION PROTECTION
    // =========================================================

    @Test
    void managerCannotChangeStatusOfVehicleFromAnotherOrganization()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository
                .findByIdForUpdate(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        assertThrows(
                ResourceNotFoundException.class,
                () ->
                        fixture.service.updateVehicleStatus(
                                1L,
                                999L,
                                10L,
                                new UpdateVehicleStatusRequest(
                                        Vehicle.VehicleStatus.INACTIVE
                                )
                        )
        );

        verify(
                fixture.fleetAccessService
        ).requireOrganizationManager(
                1L,
                999L
        );

        assertEquals(
                Vehicle.VehicleStatus.ACTIVE,
                fixture.vehicle.getStatus()
        );

        verifyNoInteractions(
                fixture.trackingSessionService
        );
    }

    // =========================================================
    // FIXTURE
    // =========================================================

    private static Fixture fixture()
            throws Exception {

        VehicleRepository vehicleRepository =
                mock(
                        VehicleRepository.class
                );

        OrganizationRepository organizationRepository =
                mock(
                        OrganizationRepository.class
                );

        FleetAccessService fleetAccessService =
                mock(
                        FleetAccessService.class
                );

        VehicleTrackingSessionService trackingSessionService =
                mock(
                        VehicleTrackingSessionService.class
                );

        VehicleService service =
                new VehicleService(
                        vehicleRepository,
                        organizationRepository,
                        fleetAccessService,
                        trackingSessionService
                );

        Organization organization =
                new Organization(
                        "Test Operator",
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        setField(
                Organization.class,
                organization,
                "id",
                100L
        );

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        "AA-1000",
                        Vehicle.VehicleType.MINIBUS,
                        12
                );

        setField(
                Vehicle.class,
                vehicle,
                "id",
                10L
        );

        return new Fixture(
                service,
                vehicleRepository,
                organizationRepository,
                fleetAccessService,
                trackingSessionService,
                organization,
                vehicle
        );
    }

    private static void setField(
            Class<?> type,
            Object target,
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                type.getDeclaredField(
                        fieldName
                );

        field.setAccessible(
                true
        );

        field.set(
                target,
                value
        );
    }

    private record Fixture(

            VehicleService service,

            VehicleRepository vehicleRepository,

            OrganizationRepository organizationRepository,

            FleetAccessService fleetAccessService,

            VehicleTrackingSessionService trackingSessionService,

            Organization organization,

            Vehicle vehicle

    ) {
    }
}
