package com.mobilityos.location.service;

import com.mobilityos.location.dto.LocationObservationRequest;
import com.mobilityos.location.event.LocationEventPublisher;
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
import java.util.List;
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

    private LocationEventPublisher eventPublisher;

    private LocationObservationIngestionService service;

    @BeforeEach
    void setUp() {

        liveStateStore =
                mock(
                        VehicleLiveStateStore.class
                );

        eventPublisher =
                mock(
                        LocationEventPublisher.class
                );

        service =
                new LocationObservationIngestionService(
                        new LocationObservationFactory(),
                        new LocationQualityPolicy(),
                        liveStateStore,
                        eventPublisher
                );
    }

    @Test
    void acceptableObservationUsesServerIdentityUpdatesLiveStateAndPublishes() {

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

        verify(
                eventPublisher
        ).publish(
                same(observation)
        );
    }

    @Test
    void poorAccuracyTouchesNeitherLiveStateNorPublisher() {

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
                liveStateStore,
                eventPublisher
        );
    }

    @Test
    void futurePhoneTimestampTouchesNeitherLiveStateNorPublisher() {

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
                liveStateStore,
                eventPublisher
        );
    }

    @Test
    void duplicateCurrentIsPublishedForRetrySafety() {

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

        verify(
                eventPublisher
        ).publish(
                any(LocationObservation.class)
        );
    }

    @Test
    void staleSequenceIsPublishedForDurableHistory() {

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

        verify(
                eventPublisher
        ).publish(
                any(LocationObservation.class)
        );
    }

    @Test
    void noActiveSessionDoesNotPublish() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.NO_ACTIVE_SESSION
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.NO_ACTIVE_SESSION,
                result
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void sessionMismatchDoesNotPublish() {

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

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void userMismatchDoesNotPublish() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.USER_MISMATCH
        );

        LocationObservationIngestionResult result =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                );

        assertEquals(
                LocationObservationIngestionResult.USER_MISMATCH,
                result
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void deviceMismatchDoesNotPublish() {

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

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void publisherFailurePropagatesSoClientCanRetry() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.APPLIED
        );

        doThrow(
                new IllegalStateException(
                        "stream unavailable"
                )
        ).when(
                eventPublisher
        ).publish(
                any(LocationObservation.class)
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        validRequest()
                )
        );

        verify(
                eventPublisher
        ).publish(
                any(LocationObservation.class)
        );
    }

    @Test
    void retryAfterPublishFailurePublishesAgainWhenLiveStateReportsDuplicate() {

        when(
                liveStateStore.applyForLiveState(
                        any(LocationObservation.class)
                )
        ).thenReturn(
                LiveObservationResult.APPLIED,
                LiveObservationResult.DUPLICATE_CURRENT
        );

        doThrow(
                new IllegalStateException(
                        "first publish failed"
                )
        )
                .doNothing()
                .when(
                        eventPublisher
                )
                .publish(
                        any(LocationObservation.class)
                );

        LocationObservationRequest request =
                validRequest();

        assertThrows(
                IllegalStateException.class,
                () -> service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        request
                )
        );

        LocationObservationIngestionResult retryResult =
                service.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        request
                );

        assertEquals(
                LocationObservationIngestionResult.DUPLICATE_CURRENT,
                retryResult
        );

        ArgumentCaptor<LocationObservation> captor =
                ArgumentCaptor.forClass(
                        LocationObservation.class
                );

        verify(
                eventPublisher,
                times(2)
        ).publish(
                captor.capture()
        );

        List<LocationObservation> attempts =
                captor.getAllValues();

        assertEquals(
                2,
                attempts.size()
        );

        assertEquals(
                OBSERVATION_ID,
                attempts.get(0).observationId()
        );

        assertEquals(
                OBSERVATION_ID,
                attempts.get(1).observationId()
        );

        assertEquals(
                attempts.get(0).observationId(),
                attempts.get(1).observationId()
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