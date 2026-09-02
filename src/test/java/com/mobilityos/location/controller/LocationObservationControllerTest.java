package com.mobilityos.location.controller;

import com.mobilityos.location.dto.LocationObservationRequest;
import com.mobilityos.location.dto.LocationObservationResponse;
import com.mobilityos.location.observation.LocationObservationIngestionResult;
import com.mobilityos.location.service.LocationObservationIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationObservationControllerTest {

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

    private LocationObservationIngestionService ingestionService;

    private LocationObservationController controller;

    @BeforeEach
    void setUp() {

        ingestionService =
                mock(
                        LocationObservationIngestionService.class
                );

        controller =
                new LocationObservationController(
                        ingestionService
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                USER_ID,
                                null,
                                List.of()
                        )
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliedObservationReturnsOk() {

        assertResponse(
                LocationObservationIngestionResult.APPLIED,
                200
        );
    }

    @Test
    void duplicateObservationReturnsOk() {

        assertResponse(
                LocationObservationIngestionResult.DUPLICATE_CURRENT,
                200
        );
    }

    @Test
    void staleObservationReturnsOk() {

        assertResponse(
                LocationObservationIngestionResult.STALE_SEQUENCE,
                200
        );
    }

    @Test
    void poorQualityObservationReturns422() {

        assertResponse(
                LocationObservationIngestionResult.REJECTED_QUALITY,
                422
        );
    }

    @Test
    void missingTrackingRuntimeReturnsConflict() {

        assertResponse(
                LocationObservationIngestionResult.NO_ACTIVE_SESSION,
                409
        );
    }

    @Test
    void wrongTrackingSessionReturnsConflict() {

        assertResponse(
                LocationObservationIngestionResult.SESSION_MISMATCH,
                409
        );
    }

    @Test
    void wrongDeviceReturnsConflict() {

        assertResponse(
                LocationObservationIngestionResult.DEVICE_MISMATCH,
                409
        );
    }

    @Test
    void wrongAuthenticatedUserReturnsForbidden() {

        assertResponse(
                LocationObservationIngestionResult.USER_MISMATCH,
                403
        );
    }

    private void assertResponse(
            LocationObservationIngestionResult ingestionResult,
            int expectedStatus
    ) {
        LocationObservationRequest request =
                validRequest();

        when(
                ingestionService.ingest(
                        USER_ID,
                        VEHICLE_ID,
                        request
                )
        ).thenReturn(
                ingestionResult
        );

        ResponseEntity<LocationObservationResponse> response =
                controller.ingest(
                        VEHICLE_ID,
                        request
                );

        assertEquals(
                expectedStatus,
                response.getStatusCode().value()
        );

        assertNotNull(
                response.getBody()
        );

        assertEquals(
                ingestionResult,
                response.getBody().result()
        );

        verify(
                ingestionService
        ).ingest(
                USER_ID,
                VEHICLE_ID,
                request
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