package com.mobilityos.location.event.history;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import com.mobilityos.location.service.VehicleLocationHistoryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationHistoryEventHandlerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private VehicleLocationHistoryService historyService;

    @Mock
    private Vehicle vehicle;

    @Mock
    private User user;

    private LocationHistoryEventHandler handler;

    @BeforeEach
    void setUp() {

        handler =
                new LocationHistoryEventHandler(
                        entityManager,
                        historyService
                );
    }

    @Test
    void resolvesEntityReferencesAndDelegatesObservation() {

        LocationObservation observation =
                observation();

        when(
                entityManager.getReference(
                        Vehicle.class,
                        observation.vehicleId()
                )
        ).thenReturn(vehicle);

        when(
                entityManager.getReference(
                        User.class,
                        observation.submittedByUserId()
                )
        ).thenReturn(user);

        handler.handle(
                observation
        );

        verify(entityManager)
                .getReference(
                        Vehicle.class,
                        observation.vehicleId()
                );

        verify(entityManager)
                .getReference(
                        User.class,
                        observation.submittedByUserId()
                );

        verify(historyService)
                .recordIfUseful(
                        vehicle,
                        user,
                        observation
                );
    }

    @Test
    void historyFailurePropagatesSoConsumerCannotAcknowledge() {

        LocationObservation observation =
                observation();

        when(
                entityManager.getReference(
                        Vehicle.class,
                        observation.vehicleId()
                )
        ).thenReturn(vehicle);

        when(
                entityManager.getReference(
                        User.class,
                        observation.submittedByUserId()
                )
        ).thenReturn(user);

        IllegalStateException databaseFailure =
                new IllegalStateException(
                        "database unavailable"
                );

        doThrow(databaseFailure)
                .when(historyService)
                .recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                handler.handle(
                                        observation
                                )
                );

        assertSame(
                databaseFailure,
                thrown
        );
    }

    private LocationObservation observation() {

        return new LocationObservation(
                UUID.fromString(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
                ),
                UUID.fromString(
                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
                ),
                847L,
                10L,
                501L,
                UUID.fromString(
                        "11111111-1111-4111-8111-111111111111"
                ),
                9.0105,
                38.7612,
                8.4,
                125.0,
                7.5,
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                ),
                Instant.parse(
                        "2026-09-02T08:00:01Z"
                ),
                LocationSource.MOBILE
        );
    }
}