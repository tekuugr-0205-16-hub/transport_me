package com.mobilityos.fleet.vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository
        extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlateNumber(
            String plateNumber
    );

    boolean existsByPlateNumber(
            String plateNumber
    );

    List<Vehicle> findByOperatorId(
            Long organizationId
    );

    /*
     * Lifecycle lock.
     *
     * Tracking start and vehicle-status transitions use this
     * same row lock so:
     *
     * - tracking cannot start while deactivation is committing
     * - deactivation cannot pass an in-progress tracking start
     *
     * The caller must have an active database transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select vehicle
            from Vehicle vehicle
            where vehicle.id = :vehicleId
            """)
    Optional<Vehicle> findByIdForUpdate(
            @Param("vehicleId") Long vehicleId
    );
}