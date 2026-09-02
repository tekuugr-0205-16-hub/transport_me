package com.mobilityos.route.entity;

import com.mobilityos.common.auditing.AuditableEntity;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vehicle_routes")
public class VehicleRoute extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_by_user_id", nullable = false)
    private User setByUser;

    @Column(name = "origin_name", nullable = false, length = 255)
    private String originName;

    @Column(name = "destination_name", nullable = false, length = 255)
    private String destinationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RouteStatus status = RouteStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected VehicleRoute() {
        // JPA
    }

    public VehicleRoute(
            Vehicle vehicle,
            User setByUser,
            String originName,
            String destinationName
    ) {
        this.vehicle = Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );

        this.setByUser = Objects.requireNonNull(
                setByUser,
                "route operator must not be null"
        );

        this.originName = requireLocationName(
                originName,
                "origin name"
        );

        this.destinationName = requireLocationName(
                destinationName,
                "destination name"
        );

        this.startedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public User getSetByUser() {
        return setByUser;
    }

    public String getOriginName() {
        return originName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public boolean isActive() {
        return status == RouteStatus.ACTIVE;
    }

    public void end(
            Instant endedAt
    ) {
        if (status == RouteStatus.ENDED) {
            return;
        }

        Instant endTime = Objects.requireNonNull(
                endedAt,
                "route end time must not be null"
        );

        if (endTime.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "route end time cannot be before start time"
            );
        }

        this.status = RouteStatus.ENDED;
        this.endedAt = endTime;
    }

    private static String requireLocationName(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        String normalized = value.trim();

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed 255 characters"
            );
        }

        return normalized;
    }

    public enum RouteStatus {
        ACTIVE,
        ENDED
    }
}