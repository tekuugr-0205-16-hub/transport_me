package com.mobilityos.fleet.membership;

import com.mobilityos.common.auditing.AuditableEntity;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(
        name = "vehicle_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vehicle_member",
                        columnNames = {"vehicle_id", "user_id"}
                )
        }
)
public class VehicleMember extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected VehicleMember() {
        // JPA
    }

    public VehicleMember(
            Vehicle vehicle,
            User user
    ) {
        this.vehicle = Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );

        this.user = Objects.requireNonNull(
                user,
                "user must not be null"
        );
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public User getUser() {
        return user;
    }
}