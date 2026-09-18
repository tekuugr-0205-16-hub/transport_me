package com.mobilityos.location.entity;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "vehicle_id",
            nullable = false,
            updatable = false
    )
    private Vehicle vehicle;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "submitted_by_user_id",
            nullable = false,
            updatable = false
    )
    private User submittedByUser;

    /*
     * Canonical GPS identity.
     *
     * These fields are null for legacy synchronous GPS rows.
     * Canonical async observations must provide all three.
     */
    @Column(
            name = "observation_id",
            updatable = false
    )
    private UUID observationId;

    @Column(
            name = "tracking_session_id",
            updatable = false
    )
    private UUID trackingSessionId;

    @Column(
            name = "sequence_number",
            updatable = false
    )
    private Long sequenceNumber;

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

    /*
     * Legacy synchronous GPS constructor.
     *
     * Legacy rows intentionally do not have canonical
     * observation/session/sequence identity.
     */
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
        this(
                vehicle,
                submittedByUser,
                latitude,
                longitude,
                speed,
                heading,
                accuracyMeters,
                recordedAt,
                Instant.now()
        );
    }

    /*
     * Legacy-compatible constructor with an explicit
     * receivedAt timestamp.
     *
     * This remains available for existing callers and tests.
     */
    public VehicleLocationHistory(
            Vehicle vehicle,
            User submittedByUser,
            double latitude,
            double longitude,
            Double speed,
            Double heading,
            Double accuracyMeters,
            Instant recordedAt,
            Instant receivedAt
    ) {
        this(
                vehicle,
                submittedByUser,
                null,
                null,
                null,
                latitude,
                longitude,
                speed,
                heading,
                accuracyMeters,
                recordedAt,
                receivedAt
        );
    }

    /*
     * Canonical asynchronous GPS constructor.
     *
     * Canonical observations preserve:
     *
     * - observation ID
     * - tracking session ID
     * - sequence number
     * - original server receivedAt timestamp
     */
    public VehicleLocationHistory(
            Vehicle vehicle,
            User submittedByUser,
            UUID observationId,
            UUID trackingSessionId,
            Long sequenceNumber,
            double latitude,
            double longitude,
            Double speed,
            Double heading,
            Double accuracyMeters,
            Instant recordedAt,
            Instant receivedAt
    ) {
        this.vehicle =
                Objects.requireNonNull(
                        vehicle,
                        "vehicle must not be null"
                );

        this.submittedByUser =
                Objects.requireNonNull(
                        submittedByUser,
                        "submittedByUser must not be null"
                );

        validateCanonicalIdentity(
                observationId,
                trackingSessionId,
                sequenceNumber
        );

        validateLatitude(latitude);
        validateLongitude(longitude);
        validateSpeed(speed);
        validateHeading(heading);
        validateAccuracy(accuracyMeters);

        this.observationId =
                observationId;

        this.trackingSessionId =
                trackingSessionId;

        this.sequenceNumber =
                sequenceNumber;

        this.latitude =
                latitude;

        this.longitude =
                longitude;

        this.speed =
                speed;

        this.heading =
                heading;

        this.accuracyMeters =
                accuracyMeters;

        this.recordedAt =
                Objects.requireNonNull(
                        recordedAt,
                        "recordedAt must not be null"
                );

        this.receivedAt =
                Objects.requireNonNull(
                        receivedAt,
                        "receivedAt must not be null"
                );
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

    public UUID getObservationId() {
        return observationId;
    }

    public UUID getTrackingSessionId() {
        return trackingSessionId;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
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

    private static void validateCanonicalIdentity(
            UUID observationId,
            UUID trackingSessionId,
            Long sequenceNumber
    ) {
        boolean allNull =
                observationId == null
                        && trackingSessionId == null
                        && sequenceNumber == null;

        boolean allPresent =
                observationId != null
                        && trackingSessionId != null
                        && sequenceNumber != null;

        if (!allNull && !allPresent) {
            throw new IllegalArgumentException(
                    "canonical location identity must be entirely present or absent"
            );
        }

        if (allPresent
                && sequenceNumber <= 0) {

            throw new IllegalArgumentException(
                    "sequenceNumber must be positive"
            );
        }
    }

    private static void validateLatitude(
            double latitude
    ) {
        if (!Double.isFinite(latitude)
                || latitude < -90.0
                || latitude > 90.0) {

            throw new IllegalArgumentException(
                    "latitude must be between -90 and 90"
            );
        }
    }

    private static void validateLongitude(
            double longitude
    ) {
        if (!Double.isFinite(longitude)
                || longitude < -180.0
                || longitude > 180.0) {

            throw new IllegalArgumentException(
                    "longitude must be between -180 and 180"
            );
        }
    }

    private static void validateSpeed(
            Double speed
    ) {
        if (speed != null
                && (!Double.isFinite(speed)
                || speed < 0.0)) {

            throw new IllegalArgumentException(
                    "speed must be zero or positive"
            );
        }
    }

    private static void validateHeading(
            Double heading
    ) {
        if (heading != null
                && (!Double.isFinite(heading)
                || heading < 0.0
                || heading >= 360.0)) {

            throw new IllegalArgumentException(
                    "heading must be between 0 and less than 360"
            );
        }
    }

    private static void validateAccuracy(
            Double accuracyMeters
    ) {
        if (accuracyMeters != null
                && (!Double.isFinite(accuracyMeters)
                || accuracyMeters < 0.0)) {

            throw new IllegalArgumentException(
                    "accuracyMeters must be zero or positive"
            );
        }
    }
}