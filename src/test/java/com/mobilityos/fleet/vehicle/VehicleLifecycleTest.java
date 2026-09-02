package com.mobilityos.fleet.vehicle;

import com.mobilityos.organization.entity.Organization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleLifecycleTest {

    @Test
    void newVehicleIsActiveForCurrentMvp() {


        Organization organization = new Organization(
                "Test Operator",
                Organization.OrganizationType.PRIVATE_OWNER
        );

        Vehicle vehicle = new Vehicle(
                organization,
                "AA-TEST-1",
                Vehicle.VehicleType.MINIBUS,
                12
        );
        assertTrue(vehicle.isActive());
    }

    @Test
    void inactiveAndMaintenanceVehiclesAreNotActive() {
        Organization organization = new Organization(
                "Test Operator",
                Organization.OrganizationType.PRIVATE_OWNER
        );

        Vehicle vehicle = new Vehicle(
                organization,
                "AA-TEST-1",
                Vehicle.VehicleType.MINIBUS,
                12
        );
        vehicle.setStatus(Vehicle.VehicleStatus.INACTIVE);
        assertFalse(vehicle.isActive());

        vehicle.setStatus(Vehicle.VehicleStatus.UNDER_MAINTENANCE);
        assertFalse(vehicle.isActive());
    }
}
