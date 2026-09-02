package com.mobilityos.location.tracking;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicle_tracking_sessions")
public class VehicleTrackingSession {

    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vehicle_id",
            nullable = false,
            updatable = false
    )
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @Column(
            name = "device_installation_id",
            nullable = false,
            updatable = false
    )
    private UUID deviceInstallationId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private TrackingSessionStatus status;

    @Column(
            name = "started_at",
            nullable = false,
            updatable = false
    )
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "end_reason",
            length = 40
    )
    private TrackingSessionEndReason endReason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected VehicleTrackingSession() {
        // Required by JPA
    }

    public VehicleTrackingSession(
            Vehicle vehicle,
            User user,
            UUID deviceInstallationId
    ) {
        this.vehicle =
                Objects.requireNonNull(
                        vehicle,
                        "vehicle must not be null"
                );

        this.user =
                Objects.requireNonNull(
                        user,
                        "user must not be null"
                );

        this.deviceInstallationId =
                Objects.requireNonNull(
                        deviceInstallationId,
                        "deviceInstallationId must not be null"
                );

        Instant now =
                Instant.now();

        this.id =
                UUID.randomUUID();

        this.status =
                TrackingSessionStatus.ACTIVE;

        this.startedAt =
                now;

        this.createdAt =
                now;

        this.updatedAt =
                now;

        this.endedAt =
                null;

        this.endReason =
                null;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public User getUser() {
        return user;
    }

    public UUID getDeviceInstallationId() {
        return deviceInstallationId;
    }

    public TrackingSessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public TrackingSessionEndReason getEndReason() {
        return endReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == TrackingSessionStatus.ACTIVE;
    }

    public void end(
            Instant endedAt,
            TrackingSessionEndReason endReason
    ) {
        Objects.requireNonNull(
                endedAt,
                "endedAt must not be null"
        );

        Objects.requireNonNull(
                endReason,
                "endReason must not be null"
        );

        /*
         * END is intentionally idempotent.
         *
         * Retain the original timestamp and reason.
         */
        if (status == TrackingSessionStatus.ENDED) {
            return;
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "endedAt cannot be before startedAt"
            );
        }

        this.status =
                TrackingSessionStatus.ENDED;

        this.endedAt =
                endedAt;

        this.endReason =
                endReason;
    }

    @PrePersist
    private void beforeInsert() {

        Instant now =
                Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }

        if (status == null) {
            status =
                    TrackingSessionStatus.ACTIVE;
        }

        if (startedAt == null) {
            startedAt = now;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt =
                Instant.now();
    }
}