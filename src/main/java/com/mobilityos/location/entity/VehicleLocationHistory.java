package com.mobilityos.location.entity;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "vehicle_location_history",
        indexes = {
                @Index(
                        name = "idx_location_history_vehicle_recorded_at",
                        columnList = "vehicle_id, recorded_at"
                ),
                @Index(
                        name = "idx_location_history_recorded_at",
                        columnList = "recorded_at"
                )
        }
)
public class VehicleLocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vehicle_id",
            nullable = false,
            updatable = false
    )
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "submitted_by_user_id",
            nullable = false,
            updatable = false
    )
    private User submittedByUser;

    @Column(
            name = "latitude",
            nullable = false,
            updatable = false
    )
    private double latitude;

    @Column(
            name = "longitude",
            nullable = false,
            updatable = false
    )
    private double longitude;

    @Column(
            name = "speed",
            updatable = false
    )
    private Double speed;

    @Column(
            name = "heading",
            updatable = false
    )
    private Double heading;

    @Column(
            name = "accuracy_meters",
            updatable = false
    )
    private Double accuracyMeters;

    @Column(
            name = "recorded_at",
            nullable = false,
            updatable = false
    )
    private Instant recordedAt;

    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    protected VehicleLocationHistory() {
        // Required by JPA
    }

    public VehicleLocationHistory(
            Vehicle vehicle,
            User submittedByUser,
            double latitude,
            double longitude,
            Double speed,
            Double heading,
            Double accuracyMeters,
            Instant recordedAt
    ) {
        this.vehicle = Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );

        this.submittedByUser = Objects.requireNonNull(
                submittedByUser,
                "submittedByUser must not be null"
        );

        validateLatitude(latitude);
        validateLongitude(longitude);
        validateSpeed(speed);
        validateHeading(heading);
        validateAccuracy(accuracyMeters);

        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heading = heading;
        this.accuracyMeters = accuracyMeters;

        this.recordedAt = Objects.requireNonNull(
                recordedAt,
                "recordedAt must not be null"
        );

        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public User getSubmittedByUser() {
        return submittedByUser;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Double getSpeed() {
        return speed;
    }

    public Double getHeading() {
        return heading;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    private static void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude)
                || latitude < -90.0
                || latitude > 90.0) {

            throw new IllegalArgumentException(
                    "latitude must be between -90 and 90"
            );
        }
    }

    private static void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude)
                || longitude < -180.0
                || longitude > 180.0) {

            throw new IllegalArgumentException(
                    "longitude must be between -180 and 180"
            );
        }
    }

    private static void validateSpeed(Double speed) {
        if (speed != null
                && (!Double.isFinite(speed) || speed < 0.0)) {

            throw new IllegalArgumentException(
                    "speed must be zero or positive"
            );
        }
    }

    private static void validateHeading(Double heading) {
        if (heading != null
                && (!Double.isFinite(heading)
                || heading < 0.0
                || heading >= 360.0)) {

            throw new IllegalArgumentException(
                    "heading must be between 0 and less than 360"
            );
        }
    }

    private static void validateAccuracy(Double accuracyMeters) {
        if (accuracyMeters != null
                && (!Double.isFinite(accuracyMeters)
                || accuracyMeters < 0.0)) {

            throw new IllegalArgumentException(
                    "accuracyMeters must be zero or positive"
            );
        }
    }
}