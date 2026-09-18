package com.mobilityos.location.repository;

import com.mobilityos.location.entity.VehicleLocationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VehicleLocationHistoryRepository
        extends JpaRepository<VehicleLocationHistory, Long> {

    Optional<VehicleLocationHistory>
    findFirstByVehicleIdOrderByRecordedAtDesc(
            Long vehicleId
    );

    Optional<VehicleLocationHistory>
    findByObservationId(
            UUID observationId
    );

    boolean existsByObservationId(
            UUID observationId
    );

    long countByObservationId(
            UUID observationId
    );

    Slice<VehicleLocationHistory>
    findByVehicleIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            Long vehicleId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable
    );
}