package com.mobilityos.location.event.history;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.event.LocationEventHandler;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.service.VehicleLocationHistoryService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class LocationHistoryEventHandler
        implements LocationEventHandler {

    private final EntityManager entityManager;

    private final VehicleLocationHistoryService
            historyService;

    public LocationHistoryEventHandler(
            EntityManager entityManager,
            VehicleLocationHistoryService historyService
    ) {
        this.entityManager =
                Objects.requireNonNull(
                        entityManager,
                        "entityManager must not be null"
                );

        this.historyService =
                Objects.requireNonNull(
                        historyService,
                        "historyService must not be null"
                );
    }

    /*
     * Transaction completes before the Redis consumer
     * acknowledges the stream record.
     *
     * Therefore:
     *
     * PostgreSQL failure -> exception -> NO ACK.
     */
    @Override
    @Transactional
    public void handle(
            LocationObservation observation
    ) {
        Objects.requireNonNull(
                observation,
                "observation must not be null"
        );

        /*
         * These are references, not normal SELECT lookups.
         *
         * The authenticated runtime already established
         * vehicle/user authority before the event entered
         * the stream. We avoid re-querying both entities
         * for every high-frequency GPS event.
         */
        Vehicle vehicle =
                entityManager.getReference(
                        Vehicle.class,
                        observation.vehicleId()
                );

        User submittedByUser =
                entityManager.getReference(
                        User.class,
                        observation.submittedByUserId()
                );

        historyService.recordIfUseful(
                vehicle,
                submittedByUser,
                observation
        );
    }
}