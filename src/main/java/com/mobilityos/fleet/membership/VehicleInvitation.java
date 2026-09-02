package com.mobilityos.fleet.membership;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vehicle_invitations")
public class VehicleInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "vehicle_id",
            nullable = false
    )
    private Vehicle vehicle;

    @Column(
            name = "invited_phone_number",
            nullable = false,
            length = 20
    )
    private String invitedPhoneNumber;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "invited_by_user_id",
            nullable = false
    )
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private InvitationStatus status =
            InvitationStatus.PENDING;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected VehicleInvitation() {
        // JPA
    }

    public VehicleInvitation(
            Vehicle vehicle,
            String invitedPhoneNumber,
            User invitedBy
    ) {
        this.vehicle = Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );

        this.invitedPhoneNumber =
                requirePhoneNumber(invitedPhoneNumber);

        this.invitedBy = Objects.requireNonNull(
                invitedBy,
                "inviter must not be null"
        );
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getInvitedPhoneNumber() {
        return invitedPhoneNumber;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void accept(
            Instant respondedAt
    ) {
        this.status =
                InvitationStatus.ACCEPTED;

        this.respondedAt =
                Objects.requireNonNull(
                        respondedAt,
                        "response time must not be null"
                );
    }

    public void decline(
            Instant respondedAt
    ) {
        this.status =
                InvitationStatus.DECLINED;

        this.respondedAt =
                Objects.requireNonNull(
                        respondedAt,
                        "response time must not be null"
                );
    }

    private static String requirePhoneNumber(
            String phoneNumber
    ) {
        if (phoneNumber == null
                || phoneNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "invited phone number must not be blank"
            );
        }

        String normalized =
                phoneNumber.trim();

        if (normalized.length() > 20) {
            throw new IllegalArgumentException(
                    "invited phone number must not exceed 20 characters"
            );
        }

        return normalized;
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }
}