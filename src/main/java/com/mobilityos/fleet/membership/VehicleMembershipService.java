package com.mobilityos.fleet.membership;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.membership.dto.InvitationResponse;
import com.mobilityos.fleet.membership.dto.InviteMemberRequest;
import com.mobilityos.fleet.membership.dto.VehicleMemberResponse;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class VehicleMembershipService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMemberRepository vehicleMemberRepository;
    private final VehicleInvitationRepository vehicleInvitationRepository;
    private final UserRepository userRepository;
    private final FleetAccessService fleetAccessService;

    public VehicleMembershipService(
            VehicleRepository vehicleRepository,
            VehicleMemberRepository vehicleMemberRepository,
            VehicleInvitationRepository vehicleInvitationRepository,
            UserRepository userRepository,
            FleetAccessService fleetAccessService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMemberRepository = vehicleMemberRepository;
        this.vehicleInvitationRepository = vehicleInvitationRepository;
        this.userRepository = userRepository;
        this.fleetAccessService = fleetAccessService;
    }

    // =========================================================
    // INVITE VEHICLE MEMBER
    // =========================================================

    @Transactional
    public InvitationResponse inviteMember(
            Long currentUserId,
            Long vehicleId,
            InviteMemberRequest request
    ) {
        /*
         * Only Organization OWNER / ADMIN may assign
         * operational access to a vehicle.
         */
        fleetAccessService.requireVehicleManager(
                currentUserId,
                vehicleId
        );

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Vehicle not found"
                        )
                );

        User inviter = getUser(currentUserId);

        /*
         * If the phone number already belongs to a registered user,
         * make sure that user is not already assigned to this vehicle.
         */
        userRepository.findByPhoneNumber(request.phoneNumber())
                .ifPresent(invitedUser -> {

                    if (vehicleMemberRepository
                            .existsByVehicleIdAndUserId(
                                    vehicleId,
                                    invitedUser.getId()
                            )) {

                        throw new ConflictException(
                                "This user already has access to the vehicle"
                        );
                    }
                });

        /*
         * Fast application-level duplicate check.
         *
         * The database partial unique index remains the final
         * concurrency protection.
         */
        if (vehicleInvitationRepository
                .findByVehicleIdAndInvitedPhoneNumberAndStatus(
                        vehicleId,
                        request.phoneNumber(),
                        VehicleInvitation.InvitationStatus.PENDING
                )
                .isPresent()) {

            throw new ConflictException(
                    "A pending invitation already exists for this phone number"
            );
        }

        VehicleInvitation invitation =
                new VehicleInvitation(
                        vehicle,
                        request.phoneNumber(),
                        inviter
                );

        try {
            /*
             * Flush immediately so a concurrent duplicate is detected
             * here and translated into a clean 409 Conflict.
             */
            VehicleInvitation savedInvitation =
                    vehicleInvitationRepository.saveAndFlush(
                            invitation
                    );

            return InvitationResponse.from(
                    savedInvitation
            );

        } catch (DataIntegrityViolationException exception) {

            throw new ConflictException(
                    "A pending invitation already exists for this phone number"
            );
        }
    }

    // =========================================================
    // CURRENT USER'S PENDING INVITATIONS
    // =========================================================

    @Transactional(readOnly = true)
    public List<InvitationResponse> getPendingInvitationsForUser(
            Long currentUserId
    ) {
        User currentUser = getUser(currentUserId);

        return vehicleInvitationRepository
                .findByInvitedPhoneNumberAndStatusOrderByCreatedAtDesc(
                        currentUser.getPhoneNumber(),
                        VehicleInvitation.InvitationStatus.PENDING
                )
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    // =========================================================
    // ACCEPT INVITATION
    // =========================================================

    @Transactional
    public VehicleResponse acceptInvitation(
            Long currentUserId,
            Long invitationId
    ) {
        /*
         * Pessimistic locking prevents accept/decline requests
         * from modifying the same invitation concurrently.
         */
        VehicleInvitation invitation =
                vehicleInvitationRepository
                        .findByIdForUpdate(invitationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Invitation not found"
                                )
                        );

        User currentUser = getUser(currentUserId);

        assertInvitationBelongsToUser(
                invitation,
                currentUser
        );

        /*
         * Repeated acceptance is intentionally idempotent
         * when the corresponding VehicleMember still exists.
         */
        if (invitation.getStatus()
                == VehicleInvitation.InvitationStatus.ACCEPTED) {

            if (vehicleMemberRepository
                    .existsByVehicleIdAndUserId(
                            invitation.getVehicle().getId(),
                            currentUserId
                    )) {

                return VehicleResponse.from(
                        invitation.getVehicle()
                );
            }

            /*
             * ACCEPTED without membership represents inconsistent
             * state. Do not silently recreate access.
             */
            throw new ConflictException(
                    "Invitation is accepted but vehicle access is missing"
            );
        }

        if (invitation.getStatus()
                != VehicleInvitation.InvitationStatus.PENDING) {

            throw new ConflictException(
                    "This invitation is no longer pending"
            );
        }

        if (!vehicleMemberRepository
                .existsByVehicleIdAndUserId(
                        invitation.getVehicle().getId(),
                        currentUserId
                )) {

            VehicleMember membership =
                    new VehicleMember(
                            invitation.getVehicle(),
                            currentUser
                    );

            vehicleMemberRepository.save(
                    membership
            );
        }
        invitation.accept(
                Instant.now()
        );invitation.accept(
                Instant.now()
        );

        return VehicleResponse.from(
                invitation.getVehicle()
        );
    }

    // =========================================================
    // DECLINE INVITATION
    // =========================================================

    @Transactional
    public InvitationResponse declineInvitation(
            Long currentUserId,
            Long invitationId
    ) {
        VehicleInvitation invitation =
                vehicleInvitationRepository
                        .findByIdForUpdate(invitationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Invitation not found"
                                )
                        );

        User currentUser = getUser(currentUserId);

        assertInvitationBelongsToUser(
                invitation,
                currentUser
        );

        /*
         * Repeated decline is idempotent.
         */
        if (invitation.getStatus()
                == VehicleInvitation.InvitationStatus.DECLINED) {

            return InvitationResponse.from(
                    invitation
            );
        }

        if (invitation.getStatus()
                != VehicleInvitation.InvitationStatus.PENDING) {

            throw new ConflictException(
                    "This invitation is no longer pending"
            );
        }

        invitation.decline(
                Instant.now()
        );

        return InvitationResponse.from(
                invitation
        );
    }

    // =========================================================
    // LIST MEMBERS OF A VEHICLE
    // =========================================================

    @Transactional(readOnly = true)
    public List<VehicleMemberResponse> getVehicleMembers(
            Long currentUserId,
            Long vehicleId
    ) {
        /*
         * Only the operator account's OWNER / ADMIN can
         * inspect operational assignments.
         */
        fleetAccessService.requireVehicleManager(
                currentUserId,
                vehicleId
        );

        return vehicleMemberRepository
                .findByVehicleId(vehicleId)
                .stream()
                .map(VehicleMemberResponse::from)
                .toList();
    }

    // =========================================================
    // REMOVE VEHICLE MEMBER
    // =========================================================

    @Transactional
    public void removeVehicleMember(
            Long currentUserId,
            Long vehicleId,
            Long memberUserId
    ) {
        /*
         * Only Organization OWNER / ADMIN can revoke
         * operational vehicle access.
         */
        fleetAccessService.requireVehicleManager(
                currentUserId,
                vehicleId
        );

        VehicleMember membership =
                vehicleMemberRepository
                        .findByVehicleIdAndUserId(
                                vehicleId,
                                memberUserId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle member not found"
                                )
                        );

        vehicleMemberRepository.delete(
                membership
        );
    }

    // =========================================================
    // CURRENT USER'S OPERATIONAL VEHICLES
    // =========================================================

    @Transactional(readOnly = true)
    public List<VehicleResponse> getVehiclesForUser(
            Long currentUserId
    ) {
        /*
         * This returns vehicles where the current user is
         * explicitly a VehicleMember.
         *
         * It is NOT the organization's full fleet.
         */
        return vehicleMemberRepository
                .findByUserId(currentUserId)
                .stream()
                .map(member ->
                        VehicleResponse.from(
                                member.getVehicle()
                        )
                )
                .toList();
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );
    }

    private void assertInvitationBelongsToUser(
            VehicleInvitation invitation,
            User currentUser
    ) {
        if (!currentUser.getPhoneNumber()
                .equals(invitation.getInvitedPhoneNumber())) {

            throw new ForbiddenException(
                    "This invitation was not addressed to your account"
            );
        }
    }
}