package com.mobilityos.fleet.membership;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.membership.dto.InvitationResponse;
import com.mobilityos.fleet.membership.dto.InviteMemberRequest;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.VehicleTrackingSessionService;
import com.mobilityos.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleMembershipServiceTest {

    @Test
    void rejectsDuplicatePendingInvitation() throws Exception {

        Fixture fixture = fixture();

        VehicleInvitation existing =
                new VehicleInvitation(
                        fixture.vehicle,
                        "0922000000",
                        fixture.currentUser
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        when(fixture.userRepository
                .findByPhoneNumber("0922000000"))
                .thenReturn(Optional.empty());

        when(fixture.vehicleInvitationRepository
                .findByVehicleIdAndInvitedPhoneNumberAndStatus(
                        10L,
                        "0922000000",
                        VehicleInvitation.InvitationStatus.PENDING
                ))
                .thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> fixture.service.inviteMember(
                        1L,
                        10L,
                        new InviteMemberRequest("0922000000")
                )
        );

        /*
         * Invitation management must go through the centralized
         * fleet authorization layer.
         */
        verify(fixture.fleetAccessService)
                .requireVehicleManager(1L, 10L);

        verify(
                fixture.vehicleInvitationRepository,
                never()
        ).saveAndFlush(any(VehicleInvitation.class));
    }

    @Test
    void rejectsInvitationWhenUserAlreadyHasVehicleAccess()
            throws Exception {

        Fixture fixture = fixture();

        User target =
                user(
                        2L,
                        "0922000000"
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(Optional.of(fixture.vehicle));

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        when(fixture.userRepository
                .findByPhoneNumber("0922000000"))
                .thenReturn(Optional.of(target));

        when(fixture.vehicleMemberRepository
                .existsByVehicleIdAndUserId(
                        10L,
                        2L
                ))
                .thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> fixture.service.inviteMember(
                        1L,
                        10L,
                        new InviteMemberRequest("0922000000")
                )
        );

        verify(fixture.fleetAccessService)
                .requireVehicleManager(1L, 10L);

        verify(
                fixture.vehicleInvitationRepository,
                never()
        ).saveAndFlush(any(VehicleInvitation.class));
    }

    @Test
    void listsOnlyPendingInvitationsAddressedToCurrentPhone()
            throws Exception {

        Fixture fixture = fixture();

        VehicleInvitation invitation =
                invitation(
                        100L,
                        fixture.vehicle,
                        fixture.currentUser,
                        "0911000000"
                );

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        when(fixture.vehicleInvitationRepository
                .findByInvitedPhoneNumberAndStatusOrderByCreatedAtDesc(
                        "0911000000",
                        VehicleInvitation.InvitationStatus.PENDING
                ))
                .thenReturn(List.of(invitation));

        List<InvitationResponse> result =
                fixture.service
                        .getPendingInvitationsForUser(1L);

        assertEquals(
                1,
                result.size()
        );

        InvitationResponse response =
                result.getFirst();

        assertEquals(
                100L,
                response.id()
        );

        assertEquals(
                10L,
                response.vehicleId()
        );

        assertEquals(
                20L,
                response.organizationId()
        );

        assertEquals(
                "AA-1000",
                response.plateNumber()
        );
    }

    @Test
    void acceptsPendingInvitationAndCreatesVehicleMembership()
            throws Exception {

        Fixture fixture = fixture();

        VehicleInvitation invitation =
                invitation(
                        100L,
                        fixture.vehicle,
                        fixture.currentUser,
                        "0911000000"
                );

        when(fixture.vehicleInvitationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(invitation));

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        when(fixture.vehicleMemberRepository
                .existsByVehicleIdAndUserId(
                        10L,
                        1L
                ))
                .thenReturn(false);

        VehicleResponse response =
                fixture.service
                        .acceptInvitation(
                                1L,
                                100L
                        );

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                20L,
                response.organizationId()
        );

        assertEquals(
                VehicleInvitation.InvitationStatus.ACCEPTED,
                invitation.getStatus()
        );

        verify(
                fixture.vehicleMemberRepository
        ).save(any(VehicleMember.class));
    }


    @Test
    void acceptingAlreadyAcceptedInvitationIsIdempotentWhenMembershipExists()
            throws Exception {

        Fixture fixture = fixture();

        VehicleInvitation invitation =
                invitation(
                        100L,
                        fixture.vehicle,
                        fixture.currentUser,
                        "0911000000"
                );

        invitation.accept(
                Instant.now()
        );

        when(fixture.vehicleInvitationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(invitation));

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        when(fixture.vehicleMemberRepository
                .existsByVehicleIdAndUserId(
                        10L,
                        1L
                ))
                .thenReturn(true);

        VehicleResponse response =
                fixture.service
                        .acceptInvitation(
                                1L,
                                100L
                        );

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                20L,
                response.organizationId()
        );

        verify(
                fixture.vehicleMemberRepository,
                never()
        ).save(any(VehicleMember.class));
    }


    @Test
    void declineIsIdempotent()
            throws Exception {

        Fixture fixture = fixture();

        VehicleInvitation invitation =
                invitation(
                        100L,
                        fixture.vehicle,
                        fixture.currentUser,
                        "0911000000"
                );

        when(fixture.vehicleInvitationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(invitation));

        when(fixture.userRepository.findById(1L))
                .thenReturn(Optional.of(fixture.currentUser));

        InvitationResponse first =
                fixture.service
                        .declineInvitation(
                                1L,
                                100L
                        );

        InvitationResponse second =
                fixture.service
                        .declineInvitation(
                                1L,
                                100L
                        );

        assertEquals(
                VehicleInvitation.InvitationStatus.DECLINED,
                first.status()
        );

        assertEquals(
                VehicleInvitation.InvitationStatus.DECLINED,
                second.status()
        );
    }

    @Test
    void returnsVehiclesAssignedToCurrentUser()
            throws Exception {

        Fixture fixture = fixture();

        VehicleMember membership =
                new VehicleMember(
                        fixture.vehicle,
                        fixture.currentUser
                );

        when(fixture.vehicleMemberRepository
                .findByUserId(1L))
                .thenReturn(List.of(membership));

        List<VehicleResponse> result =
                fixture.service
                        .getVehiclesForUser(1L);

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                10L,
                result.getFirst().id()
        );

        assertEquals(
                20L,
                result.getFirst().organizationId()
        );

        assertEquals(
                "AA-1000",
                result.getFirst().plateNumber()
        );
    }

    @Test
    void removingVehicleMemberTerminatesTrackingBeforeDeletingMembership()
            throws Exception {

        Fixture fixture = fixture();

        User memberUser =
                user(
                        2L,
                        "0922000000"
                );

        VehicleMember membership =
                new VehicleMember(
                        fixture.vehicle,
                        memberUser
                );

        when(fixture.vehicleMemberRepository
                .findByVehicleIdAndUserIdForUpdate(
                        10L,
                        2L
                ))
                .thenReturn(
                        Optional.of(
                                membership
                        )
                );

        fixture.service.removeVehicleMember(
                1L,
                10L,
                2L
        );

        verify(
                fixture.fleetAccessService
        ).requireVehicleManager(
                1L,
                10L
        );

        InOrder lifecycleOrder =
                inOrder(
                        fixture.trackingSessionService,
                        fixture.vehicleMemberRepository
                );

        lifecycleOrder.verify(
                fixture.trackingSessionService
        ).terminateActiveSession(
                10L,
                2L,
                TrackingSessionEndReason.MEMBERSHIP_REVOKED
        );

        lifecycleOrder.verify(
                fixture.vehicleMemberRepository
        ).delete(
                membership
        );
    }

    // =========================================================
    // FIXTURE
    // =========================================================

    private static Fixture fixture()
            throws Exception {

        VehicleRepository vehicleRepository =
                mock(VehicleRepository.class);

        VehicleMemberRepository vehicleMemberRepository =
                mock(VehicleMemberRepository.class);

        VehicleInvitationRepository vehicleInvitationRepository =
                mock(VehicleInvitationRepository.class);

        UserRepository userRepository =
                mock(UserRepository.class);

        FleetAccessService fleetAccessService =
                mock(FleetAccessService.class);

        VehicleTrackingSessionService trackingSessionService =
                mock(VehicleTrackingSessionService.class);

        /*
         * VehicleMembershipService now owns:
         *
         * - vehicle-member invitations
         * - accept / decline
         * - member management
         * - /vehicles/my
         */
        VehicleMembershipService service =
                new VehicleMembershipService(
                        vehicleRepository,
                        vehicleMemberRepository,
                        vehicleInvitationRepository,
                        userRepository,
                        fleetAccessService,
                        trackingSessionService
                );

        User currentUser =
                user(
                        1L,
                        "0911000000"
                );

        Organization organization =
                organization(
                        20L,
                        "Test Transport"
                );

        Vehicle vehicle =
                vehicle(
                        10L,
                        organization,
                        "AA-1000"
                );

        return new Fixture(
                service,
                vehicleRepository,
                vehicleMemberRepository,
                vehicleInvitationRepository,
                userRepository,
                fleetAccessService,
                trackingSessionService,
                currentUser,
                organization,
                vehicle
        );
    }

    // =========================================================
    // ENTITY BUILDERS
    // =========================================================

    private static User user(
            Long id,
            String phoneNumber
    ) throws Exception {

        User user =
                new User(
                        phoneNumber,
                        "hash"
                );

        setId(
                User.class,
                user,
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

        setId(
                Organization.class,
                organization,
                id
        );

        return organization;
    }

    private static Vehicle vehicle(
            Long id,
            Organization organization,
            String plateNumber
    ) throws Exception {

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        plateNumber,
                        Vehicle.VehicleType.MINIBUS,
                        12
                );

        setId(
                Vehicle.class,
                vehicle,
                id
        );

        return vehicle;
    }

    private static VehicleInvitation invitation(
            Long id,
            Vehicle vehicle,
            User inviter,
            String invitedPhoneNumber
    ) throws Exception {

        VehicleInvitation invitation =
                new VehicleInvitation(
                        vehicle,
                        invitedPhoneNumber,
                        inviter
                );

        setId(
                VehicleInvitation.class,
                invitation,
                id
        );

        return invitation;
    }

    // =========================================================
    // REFLECTION HELPER
    // =========================================================

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

    // =========================================================
    // FIXTURE RECORD
    // =========================================================

    private record Fixture(
            VehicleMembershipService service,
            VehicleRepository vehicleRepository,
            VehicleMemberRepository vehicleMemberRepository,
            VehicleInvitationRepository vehicleInvitationRepository,
            UserRepository userRepository,
            FleetAccessService fleetAccessService,
            VehicleTrackingSessionService trackingSessionService,
            User currentUser,
            Organization organization,
            Vehicle vehicle
    ) {
    }
}
