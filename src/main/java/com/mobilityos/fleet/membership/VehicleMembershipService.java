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
import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.VehicleTrackingSessionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class VehicleMembershipService {

    private final VehicleRepository vehicleRepository;

    private final VehicleMemberRepository vehicleMemberRepository;

    private final VehicleInvitationRepository vehicleInvitationRepository;

    private final UserRepository userRepository;

    private final FleetAccessService fleetAccessService;

    private final VehicleTrackingSessionService trackingSessionService;

    public VehicleMembershipService(
            VehicleRepository vehicleRepository,
            VehicleMemberRepository vehicleMemberRepository,
            VehicleInvitationRepository vehicleInvitationRepository,
            UserRepository userRepository,
            FleetAccessService fleetAccessService,
            VehicleTrackingSessionService trackingSessionService
    ) {
        this.vehicleRepository =
                Objects.requireNonNull(
                        vehicleRepository,
                        "vehicleRepository must not be null"
                );

        this.vehicleMemberRepository =
                Objects.requireNonNull(
                        vehicleMemberRepository,
                        "vehicleMemberRepository must not be null"
                );

        this.vehicleInvitationRepository =
                Objects.requireNonNull(
                        vehicleInvitationRepository,
                        "vehicleInvitationRepository must not be null"
                );

        this.userRepository =
                Objects.requireNonNull(
                        userRepository,
                        "userRepository must not be null"
                );

        this.fleetAccessService =
                Objects.requireNonNull(
                        fleetAccessService,
                        "fleetAccessService must not be null"
                );

        this.trackingSessionService =
                Objects.requireNonNull(
                        trackingSessionService,
                        "trackingSessionService must not be null"
                );
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

        Vehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle not found"
                                )
                        );

        User inviter =
                getUser(
                        currentUserId
                );

        /*
         * If the phone number already belongs to a registered
         * user, make sure that user is not already assigned to
         * this vehicle.
         */
        userRepository
                .findByPhoneNumber(
                        request.phoneNumber()
                )
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
             * Flush immediately so a concurrent duplicate is
             * detected here and translated into a clean
             * application-level conflict.
             */
            VehicleInvitation savedInvitation =
                    vehicleInvitationRepository
                            .saveAndFlush(
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
        User currentUser =
                getUser(
                        currentUserId
                );

        return vehicleInvitationRepository
                .findByInvitedPhoneNumberAndStatusOrderByCreatedAtDesc(
                        currentUser.getPhoneNumber(),
                        VehicleInvitation.InvitationStatus.PENDING
                )
                .stream()
                .map(
                        InvitationResponse::from
                )
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
                        .findByIdForUpdate(
                                invitationId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Invitation not found"
                                )
                        );

        User currentUser =
                getUser(
                        currentUserId
                );

        assertInvitationBelongsToUser(
                invitation,
                currentUser
        );

        /*
         * Repeated acceptance is intentionally idempotent when
         * the corresponding VehicleMember still exists.
         */
        if (invitation.getStatus()
                == VehicleInvitation.InvitationStatus.ACCEPTED) {

            if (vehicleMemberRepository
                    .existsByVehicleIdAndUserId(
                            invitation
                                    .getVehicle()
                                    .getId(),
                            currentUserId
                    )) {

                return VehicleResponse.from(
                        invitation.getVehicle()
                );
            }

            /*
             * ACCEPTED without membership represents
             * inconsistent state.
             *
             * Do not silently recreate operational access.
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
                        invitation
                                .getVehicle()
                                .getId(),
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

        /*
         * Exactly one transition from PENDING -> ACCEPTED.
         *
         * The previous duplicate invitation.accept(...) call
         * has intentionally been removed.
         */
        invitation.accept(
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
                        .findByIdForUpdate(
                                invitationId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Invitation not found"
                                )
                        );

        User currentUser =
                getUser(
                        currentUserId
                );

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
                .findByVehicleId(
                        vehicleId
                )
                .stream()
                .map(
                        VehicleMemberResponse::from
                )
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

        /*
         * IMPORTANT:
         *
         * Use the same pessimistically locked VehicleMember row
         * used by tracking start.
         *
         * This serializes:
         *
         *     startSession()
         *
         * against:
         *
         *     removeVehicleMember()
         *
         * so a tracking session cannot race past membership
         * revocation.
         */
        VehicleMember membership =
                vehicleMemberRepository
                        .findByVehicleIdAndUserIdForUpdate(
                                vehicleId,
                                memberUserId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle member not found"
                                )
                        );

        /*
         * Revoke runtime authority BEFORE deleting membership.
         *
         * VehicleTrackingSessionService joins this transaction
         * because its termination method uses the default
         * REQUIRED transaction propagation.
         *
         * If an ACTIVE session exists:
         *
         *     ACTIVE
         *       ↓
         *     ENDED
         *       ↓
         *     reason = MEMBERSHIP_REVOKED
         *
         * and the matching Redis runtime is removed.
         *
         * If there is no active session, this is safely a
         * no-op and membership removal continues.
         */
        trackingSessionService.terminateActiveSession(
                vehicleId,
                memberUserId,
                TrackingSessionEndReason.MEMBERSHIP_REVOKED
        );

        /*
         * Remove only the vehicle-access relationship.
         *
         * The User account itself remains untouched.
         */
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
         * Return only vehicles where the current user is
         * explicitly a VehicleMember.
         *
         * This is NOT the organization's complete fleet.
         */
        return vehicleMemberRepository
                .findByUserId(
                        currentUserId
                )
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

    private User getUser(
            Long userId
    ) {
        return userRepository
                .findById(
                        userId
                )
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
        if (!currentUser
                .getPhoneNumber()
                .equals(
                        invitation.getInvitedPhoneNumber()
                )) {

            throw new ForbiddenException(
                    "This invitation was not addressed to your account"
            );
        }
    }
}