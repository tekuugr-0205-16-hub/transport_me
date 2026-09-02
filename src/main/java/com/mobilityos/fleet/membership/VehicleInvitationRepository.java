package com.mobilityos.fleet.membership;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleInvitationRepository
        extends JpaRepository<VehicleInvitation, Long> {

    /**
     * Returns the user's invitations in newest-first order.
     *
     * Vehicle is fetched together with the invitation because
     * InvitationResponse needs vehicle information such as
     * vehicle id / plate number.
     */
    @EntityGraph(attributePaths = "vehicle")
    List<VehicleInvitation>
    findByInvitedPhoneNumberAndStatusOrderByCreatedAtDesc(
            String phoneNumber,
            VehicleInvitation.InvitationStatus status
    );

    /**
     * Used to prevent more than one pending invitation for the
     * same vehicle and phone number.
     *
     * The database partial unique index remains the final
     * protection against concurrent duplicate requests.
     */
    Optional<VehicleInvitation>
    findByVehicleIdAndInvitedPhoneNumberAndStatus(
            Long vehicleId,
            String phoneNumber,
            VehicleInvitation.InvitationStatus status
    );

    /**
     * Locks the invitation row while accepting or declining it.
     *
     * This prevents concurrent requests from both changing the
     * same invitation at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from VehicleInvitation invitation
            where invitation.id = :id
            """)
    Optional<VehicleInvitation> findByIdForUpdate(
            @Param("id") Long id
    );
}