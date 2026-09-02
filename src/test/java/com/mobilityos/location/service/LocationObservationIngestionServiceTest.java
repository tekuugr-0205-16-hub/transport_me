package com.mobilityos.location.service;

import com.mobilityos.location.dto.LocationObservationRequest;
import com.mobilityos.location.live.LiveObservationResult;
import com.mobilityos.location.live.VehicleLiveStateStore;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationObservationFactory;
import com.mobilityos.location.observation.LocationObservationIngestionResult;
import com.mobilityos.location.quality.LocationQualityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationObservationIngestionServiceTest {

    private static final Long USER_ID =
            501L;

    private static final Long VEHICLE_ID =
            10L;

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

    private VehicleLiveStateStore liveStateStore;

    private LocationObservationIngestionService service;

    @BeforeEach
    void setUp() {

        liveStateStore =
                mock(
                        VehicleLiveStateStore.class
                );

        service =
                new LocationObservationIngestionService(
                        new LocationObservationFactory(),
                        new LocationQualityPolicy(),
                        liveStateStore
                );
    }

    @Test
    void acceptableObservationUsesServerIdentityAndUpdatesLiveState() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.APPLIED
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.APPLIED,
                result
        );

        ArgumentCaptor<LocationObservation> captor =
                ArgumentCaptor.forClass(
                        LocationObservation.class
                );

        verify(
                liveStateStore
        ).applyForLiveState(
                captor.capture()
        );

        LocationObservation observation =
                captor.getValue();

        assertEquals(
                USER_ID,
                observation.submittedByUserId()
        );

        assertEquals(
                VEHICLE_ID,
                observation.vehicleId()
        );

        assertEquals(
                SESSION_ID,
                observation.trackingSessionId()
        );

        assertEquals(
                DEVICE_ID,
                observation.deviceInstallationId()
        );

        assertEquals(
                OBSERVATION_ID,
                observation.observationId()
        );

        assertEquals(
                847L,
                observation.sequenceNumber()
        );
    }

    @Test
    void poorAccuracyDoesNotAdvanceLiveState() {

        LocationObservationRequest request =
                new LocationObservationRequest(
                        OBSERVATION_ID,
                        SESSION_ID,
                        847L,
                        DEVICE_ID,
                        9.0105,
                        38.7612,
                        8.4,
                        125.0,
                        150.0,
                        Instant.now()
                );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        request
                );

        assertEquals(
                LocationObservationIngestionResult.REJECTED_QUALITY,
                result
        );

        verifyNoInteractions(
                liveStateStore
        );
    }

    @Test
    void futurePhoneTimestampDoesNotAdvanceLiveState() {

        LocationObservationRequest request =
                new LocationObservationRequest(
                        OBSERVATION_ID,
                        SESSION_ID,
                        847L,
                        DEVICE_ID,
                        9.0105,
                        38.7612,
                        8.4,
                        125.0,
                        7.5,
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        request
                );

        assertEquals(
                LocationObservationIngestionResult.REJECTED_QUALITY,
                result
        );

        verifyNoInteractions(
                liveStateStore
        );
    }

    @Test
    void duplicateCurrentResultIsPreserved() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.DUPLICATE_CURRENT
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.DUPLICATE_CURRENT,
                result
        );
    }

    @Test
    void staleSequenceResultIsPreserved() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.STALE_SEQUENCE
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.STALE_SEQUENCE,
                result
        );
    }

    @Test
    void sessionMismatchResultIsPreserved() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.SESSION_MISMATCH
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.SESSION_MISMATCH,
                result
        );
    }

    @Test
    void deviceMismatchResultIsPreserved() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.DEVICE_MISMATCH
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.DEVICE_MISMATCH,
                result
        );
    }

    private LocationObservationRequest validRequest() {

        return new LocationObservationRequest(
                OBSERVATION_ID,
                SESSION_ID,
                847L,
                DEVICE_ID,
                9.0105,
                38.7612,
                8.4,
                125.0,
                7.5,
                Instant.now()
                        .minusSeconds(
                                1
                        )
        );
    }
}