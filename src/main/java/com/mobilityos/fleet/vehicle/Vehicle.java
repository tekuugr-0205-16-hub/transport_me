package com.mobilityos.fleet.vehicle;

import com.mobilityos.common.auditing.AuditableEntity;
import com.mobilityos.organization.entity.Organization;
import jakarta.persistence.*;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "vehicles")
public class Vehicle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private Organization operator;

    @Column(
            name = "plate_number",
            nullable = false,
            unique = true,
            length = 20
    )
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "vehicle_type",
            nullable = false,
            length = 30
    )
    private VehicleType vehicleType;

    @Column(
            name = "capacity",
            nullable = false
    )
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "current_subscription_id")
    private Long currentSubscriptionId;

    protected Vehicle() {
        // JPA
    }

    public Vehicle(
            Organization operator,
            String plateNumber,
            VehicleType vehicleType,
            Integer capacity
    ) {
        this.operator = Objects.requireNonNull(
                operator,
                "operator account must not be null"
        );

        setPlateNumber(plateNumber);
        setVehicleType(vehicleType);
        setCapacity(capacity);
    }

    public Long getId() {
        return id;
    }

    public Organization getOperator() {
        return operator;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(
            String plateNumber
    ) {
        if (plateNumber == null
                || plateNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "plate number must not be blank"
            );
        }

        this.plateNumber = plateNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(
            VehicleType vehicleType
    ) {
        this.vehicleType = Objects.requireNonNull(
                vehicleType,
                "vehicle type must not be null"
        );
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(
            Integer capacity
    ) {
        if (capacity == null || capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        this.capacity = capacity;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(
            VehicleStatus status
    ) {
        this.status = Objects.requireNonNull(
                status,
                "vehicle status must not be null"
        );
    }

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }

    public Long getCurrentSubscriptionId() {
        return currentSubscriptionId;
    }

    public void setCurrentSubscriptionId(
            Long currentSubscriptionId
    ) {
        this.currentSubscriptionId =
                currentSubscriptionId;
    }

    public enum VehicleType {
        MINIBUS,
        BUS,
        TAXI,
        BAJAJ
    }

    public enum VehicleStatus {
        ACTIVE,
        INACTIVE,
        UNDER_MAINTENANCE
    }
}