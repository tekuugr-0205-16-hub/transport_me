package com.mobilityos.location.service;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.identity.entity.User;
import com.mobilityos.location.entity.VehicleLocationHistory;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import com.mobilityos.location.quality.LocationQualityPolicy;
import com.mobilityos.location.repository.VehicleLocationHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleLocationHistoryServiceTest {

    private static final Long VEHICLE_ID = 10L;
    private static final Long USER_ID = 501L;

    private static final UUID OBSERVATION_ID =
            UUID.fromString(
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
            );

    private static final UUID DEVICE_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    @Mock
    private VehicleLocationHistoryRepository repository;

    @Mock
    private LocationQualityPolicy locationQualityPolicy;

    @Mock
    private Vehicle vehicle;

    @Mock
    private User user;

    private VehicleLocationHistoryService service;

    @BeforeEach
    void setUp() {
        service =
                new VehicleLocationHistoryService(
                        repository,
                        locationQualityPolicy
                );
    }

    @Test
    void firstObservationIsSavedAndPreservesOriginalReceivedAt() {

        Instant recordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        Instant receivedAt =
                Instant.parse(
                        "2026-09-02T08:00:02Z"
                );

        LocationObservation observation =
                observation(
                        9.0100,
                        38.7600,
                        recordedAt,
                        receivedAt
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertTrue(saved);

        ArgumentCaptor<VehicleLocationHistory> captor =
                ArgumentCaptor.forClass(
                        VehicleLocationHistory.class
                );

        verify(repository)
                .save(
                        captor.capture()
                );

        VehicleLocationHistory persisted =
                captor.getValue();

        assertEquals(
                recordedAt,
                persisted.getRecordedAt()
        );

        assertEquals(
                receivedAt,
                persisted.getReceivedAt()
        );

        assertEquals(
                9.0100,
                persisted.getLatitude()
        );

        assertEquals(
                38.7600,
                persisted.getLongitude()
        );
    }

    @Test
    void observationUnderFiveSecondsIsSkippedEvenWhenMovedFar() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        LocationObservation observation =
                observation(
                        9.0200,
                        38.7700,
                        previousRecordedAt.plusSeconds(4),
                        previousRecordedAt.plusSeconds(5)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertFalse(saved);

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void observationAtFiveSecondsAndMovedAtLeastFifteenMetersIsSaved() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        /*
         * Approximately 22 metres north.
         */
        LocationObservation observation =
                observation(
                        9.0102,
                        38.7600,
                        previousRecordedAt.plusSeconds(5),
                        previousRecordedAt.plusSeconds(6)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertTrue(saved);

        verify(repository)
                .save(
                        any(
                                VehicleLocationHistory.class
                        )
                );
    }

    @Test
    void stationaryObservationBeforeSixtySecondsIsSkipped() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        LocationObservation observation =
                observation(
                        9.0100,
                        38.7600,
                        previousRecordedAt.plusSeconds(30),
                        previousRecordedAt.plusSeconds(31)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertFalse(saved);

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void stationaryObservationAtSixtySecondsIsSaved() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        LocationObservation observation =
                observation(
                        9.0100,
                        38.7600,
                        previousRecordedAt.plusSeconds(60),
                        previousRecordedAt.plusSeconds(61)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertTrue(saved);

        verify(repository)
                .save(
                        any(
                                VehicleLocationHistory.class
                        )
                );
    }

    @Test
    void duplicateRecordedTimestampIsSkipped() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:00Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        LocationObservation observation =
                observation(
                        9.0200,
                        38.7700,
                        previousRecordedAt,
                        previousRecordedAt.plusSeconds(1)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertFalse(saved);

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void olderObservationIsSkipped() {

        Instant previousRecordedAt =
                Instant.parse(
                        "2026-09-02T08:00:10Z"
                );

        VehicleLocationHistory latest =
                history(
                        9.0100,
                        38.7600,
                        previousRecordedAt
                );

        LocationObservation observation =
                observation(
                        9.0200,
                        38.7700,
                        previousRecordedAt.minusSeconds(5),
                        previousRecordedAt.plusSeconds(1)
                );

        accept(observation);

        when(
                repository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                VEHICLE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertFalse(saved);

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void unacceptableCanonicalObservationIsNotWritten() {

        LocationObservation observation =
                observation(
                        9.0100,
                        38.7600,
                        Instant.parse(
                                "2026-09-02T08:00:00Z"
                        ),
                        Instant.parse(
                                "2026-09-02T08:00:01Z"
                        )
                );

        when(
                locationQualityPolicy.isAcceptable(
                        observation,
                        observation.receivedAt()
                )
        ).thenReturn(false);

        boolean saved =
                service.recordIfUseful(
                        vehicle,
                        user,
                        observation
                );

        assertFalse(saved);

        verifyNoInteractions(repository);
    }

    private void accept(
            LocationObservation observation
    ) {
        /*
         * Only accepted observations reach history lookup,
         * therefore only these tests require vehicle.getId().
         *
         * Keeping this here avoids Mockito unnecessary
         * stubbing for quality-rejected observations.
         */
        when(vehicle.getId())
                .thenReturn(VEHICLE_ID);

        when(
                locationQualityPolicy.isAcceptable(
                        eq(observation),
                        eq(observation.receivedAt())
                )
        ).thenReturn(true);
    }

    private VehicleLocationHistory history(
            double latitude,
            double longitude,
            Instant recordedAt
    ) {
        return new VehicleLocationHistory(
                vehicle,
                user,
                latitude,
                longitude,
                5.0,
                90.0,
                8.0,
                recordedAt,
                recordedAt.plusSeconds(1)
        );
    }

    private LocationObservation observation(
            double latitude,
            double longitude,
            Instant recordedAt,
            Instant receivedAt
    ) {
        return new LocationObservation(
                OBSERVATION_ID,
                SESSION_ID,
                847L,
                VEHICLE_ID,
                USER_ID,
                DEVICE_ID,
                latitude,
                longitude,
                8.4,
                125.0,
                7.5,
                recordedAt,
                receivedAt,
                LocationSource.MOBILE
        );
    }
}