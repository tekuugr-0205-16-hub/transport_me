package com.mobilityos.fleet.membership;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleMemberRepository
        extends JpaRepository<VehicleMember, Long> {

    boolean existsByVehicleIdAndUserId(
            Long vehicleId,
            Long userId
    );

    Optional<VehicleMember> findByVehicleIdAndUserId(
            Long vehicleId,
            Long userId
    );

    /*
     * Lifecycle serialization lock.
     *
     * Tracking start and membership revocation must lock the
     * SAME VehicleMember row.
     *
     * This prevents:
     *
     * startSession sees membership
     *        ↓
     * manager removes membership
     *        ↓
     * startSession creates a new ACTIVE session afterward
     *
     * PESSIMISTIC_WRITE makes one operation wait for the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from VehicleMember member
            where member.vehicle.id = :vehicleId
              and member.user.id = :userId
            """)
    Optional<VehicleMember> findByVehicleIdAndUserIdForUpdate(
            @Param("vehicleId") Long vehicleId,
            @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = "vehicle")
    List<VehicleMember> findByUserId(
            Long userId
    );

    @EntityGraph(attributePaths = "user")
    List<VehicleMember> findByVehicleId(
            Long vehicleId
    );
}