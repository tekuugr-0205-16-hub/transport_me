package com.mobilityos.location.tracking;

import com.mobilityos.fleet.membership.VehicleMember;
import com.mobilityos.fleet.membership.VehicleMemberRepository;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import com.mobilityos.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TrackingRuntimeActivationCoordinatorTest {

    private static final long VEHICLE_ID = 10L;

    private static final long USER_ID = 1L;

    private static final UUID DEVICE_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    // =========================================================
    // ACTIVE + AUTHORIZED
    // =========================================================

    @Test
    void activatesRuntimeOnlyAfterMembershipAndSessionAreValidated()
            throws Exception {

        Fixture fixture = fixture();

        VehicleMember membership =
                new VehicleMember(
                        fixture.vehicle,
                        fixture.user
                );

        VehicleTrackingSession session =
                new VehicleTrackingSession(
                        fixture.vehicle,
                        fixture.user,
                        DEVICE_ID
                );

        when(fixture.vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.of(
                                membership
                        )
                );

        when(fixture.trackingSessionRepository
                .findByIdAndVehicleIdAndUserId(
                        session.getId(),
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.of(
                                session
                        )
                );

        fixture.coordinator.activateIfStillAuthorized(
                VEHICLE_ID,
                session.getId(),
                USER_ID
        );

        /*
         * Important ordering:
         *
         * 1. lock/check VehicleMember
         * 2. reread committed tracking session
         * 3. establish Redis authority
         */
        InOrder order =
                inOrder(
                        fixture.vehicleMemberRepository,
                        fixture.trackingSessionRepository,
                        fixture.runtimeStore
                );

        order.verify(
                fixture.vehicleMemberRepository
        ).findByVehicleIdAndUserIdForUpdate(
                VEHICLE_ID,
                USER_ID
        );

        order.verify(
                fixture.trackingSessionRepository
        ).findByIdAndVehicleIdAndUserId(
                session.getId(),
                VEHICLE_ID,
                USER_ID
        );

        order.verify(
                fixture.runtimeStore
        ).activateNewSession(
                VEHICLE_ID,
                session.getId(),
                USER_ID,
                DEVICE_ID,
                session.getStartedAt()
        );
    }

    // =========================================================
    // MEMBERSHIP REVOKED
    // =========================================================

    @Test
    void doesNotActivateWhenMembershipWasRevoked()
            throws Exception {

        Fixture fixture = fixture();

        UUID sessionId =
                UUID.randomUUID();

        when(fixture.vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.empty()
                );

        fixture.coordinator.activateIfStillAuthorized(
                VEHICLE_ID,
                sessionId,
                USER_ID
        );

        verify(
                fixture.vehicleMemberRepository
        ).findByVehicleIdAndUserIdForUpdate(
                VEHICLE_ID,
                USER_ID
        );

        /*
         * Once membership is gone, do not even inspect the
         * tracking session and never establish Redis authority.
         */
        verifyNoInteractions(
                fixture.trackingSessionRepository,
                fixture.runtimeStore
        );
    }

    // =========================================================
    // SESSION DISAPPEARED
    // =========================================================

    @Test
    void doesNotActivateWhenCommittedSessionNoLongerExists()
            throws Exception {

        Fixture fixture = fixture();

        VehicleMember membership =
                new VehicleMember(
                        fixture.vehicle,
                        fixture.user
                );

        UUID sessionId =
                UUID.randomUUID();

        when(fixture.vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.of(
                                membership
                        )
                );

        when(fixture.trackingSessionRepository
                .findByIdAndVehicleIdAndUserId(
                        sessionId,
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.empty()
                );

        fixture.coordinator.activateIfStillAuthorized(
                VEHICLE_ID,
                sessionId,
                USER_ID
        );

        verify(
                fixture.trackingSessionRepository
        ).findByIdAndVehicleIdAndUserId(
                sessionId,
                VEHICLE_ID,
                USER_ID
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );
    }

    // =========================================================
    // SESSION ENDED WHILE CALLBACK WAITED
    // =========================================================

    @Test
    void doesNotActivateWhenSessionWasEndedBeforeActivation()
            throws Exception {

        Fixture fixture = fixture();

        VehicleMember membership =
                new VehicleMember(
                        fixture.vehicle,
                        fixture.user
                );

        VehicleTrackingSession session =
                new VehicleTrackingSession(
                        fixture.vehicle,
                        fixture.user,
                        DEVICE_ID
                );

        /*
         * Simulates revocation / timeout / administrative end
         * happening after the original start transaction committed
         * but before this delayed activation reaches Redis.
         */
        session.end(
                Instant.now(),
                TrackingSessionEndReason.MEMBERSHIP_REVOKED
        );

        when(fixture.vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.of(
                                membership
                        )
                );

        when(fixture.trackingSessionRepository
                .findByIdAndVehicleIdAndUserId(
                        session.getId(),
                        VEHICLE_ID,
                        USER_ID
                ))
                .thenReturn(
                        Optional.of(
                                session
                        )
                );

        fixture.coordinator.activateIfStillAuthorized(
                VEHICLE_ID,
                session.getId(),
                USER_ID
        );

        verify(
                fixture.trackingSessionRepository
        ).findByIdAndVehicleIdAndUserId(
                session.getId(),
                VEHICLE_ID,
                USER_ID
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );
    }

    // =========================================================
    // FIXTURE
    // =========================================================

    private static Fixture fixture()
            throws Exception {

        VehicleMemberRepository vehicleMemberRepository =
                mock(
                        VehicleMemberRepository.class
                );

        VehicleTrackingSessionRepository trackingSessionRepository =
                mock(
                        VehicleTrackingSessionRepository.class
                );

        VehicleTrackingRuntimeStore runtimeStore =
                mock(
                        VehicleTrackingRuntimeStore.class
                );

        TrackingRuntimeActivationCoordinator coordinator =
                new TrackingRuntimeActivationCoordinator(
                        vehicleMemberRepository,
                        trackingSessionRepository,
                        runtimeStore
                );

        User user =
                user(
                        USER_ID,
                        "0911000000"
                );

        Organization organization =
                organization(
                        100L,
                        "Test Transport"
                );

        Vehicle vehicle =
                vehicle(
                        VEHICLE_ID,
                        organization,
                        "AA-1000"
                );

        return new Fixture(
                coordinator,
                vehicleMemberRepository,
                trackingSessionRepository,
                runtimeStore,
                user,
                organization,
                vehicle
        );
    }

    // =========================================================
    // ENTITY HELPERS
    // =========================================================

    private static User user(
            Long id,
            String phone
    ) throws Exception {

        User user =
                new User(
                        phone,
                        "hash"
                );

        setField(
                User.class,
                user,
                "id",
                id
        );

        return user;
    }

    private static Organization organization(
            Long id,
            String name
    ) throws Exception {

        Organization organization =
                new Organization(
                        name,
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        setField(
                Organization.class,
                organization,
                "id",
                id
        );

        return organization;
    }

    private static Vehicle vehicle(
            Long id,
            Organization organization,
            String plate
    ) throws Exception {

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        plate,
                        Vehicle.VehicleType.MINIBUS,
                        12
                );

        setField(
                Vehicle.class,
                vehicle,
                "id",
                id
        );

        return vehicle;
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

    // =========================================================
    // FIXTURE RECORD
    // =========================================================

    private record Fixture(

            TrackingRuntimeActivationCoordinator coordinator,

            VehicleMemberRepository vehicleMemberRepository,

            VehicleTrackingSessionRepository trackingSessionRepository,

            VehicleTrackingRuntimeStore runtimeStore,

            User user,

            Organization organization,

            Vehicle vehicle

    ) {
    }
}