package com.mobilityos.location.tracking;

import com.mobilityos.location.tracking.dto.TrackingSessionResponse;
import com.mobilityos.location.tracking.dto.TrackingSessionStartRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleTrackingSessionControllerTest {

    private static final Long USER_ID = 1L;

    private static final Long VEHICLE_ID = 10L;

    private static final UUID DEVICE_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
            );

    private VehicleTrackingSessionService trackingSessionService;

    private VehicleTrackingSessionController controller;

    @BeforeEach
    void setUp() {

        trackingSessionService =
                mock(
                        VehicleTrackingSessionService.class
                );

        controller =
                new VehicleTrackingSessionController(
                        trackingSessionService
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
    void startSessionUsesAuthenticatedUser() {

        TrackingSessionResponse expected =
                response(
                        TrackingSessionStatus.ACTIVE,
                        null,
                        null
                );

        when(trackingSessionService.startSession(
                USER_ID,
                VEHICLE_ID,
                DEVICE_ID
        )).thenReturn(expected);

        TrackingSessionStartRequest request =
                new TrackingSessionStartRequest(
                        DEVICE_ID
                );

        ResponseEntity<TrackingSessionResponse> response =
                controller.startSession(
                        VEHICLE_ID,
                        request
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertSame(
                expected,
                response.getBody()
        );

        verify(
                trackingSessionService
        ).startSession(
                USER_ID,
                VEHICLE_ID,
                DEVICE_ID
        );
    }

    @Test
    void getActiveSessionUsesAuthenticatedUser() {

        TrackingSessionResponse expected =
                response(
                        TrackingSessionStatus.ACTIVE,
                        null,
                        null
                );

        when(trackingSessionService.getActiveSession(
                USER_ID,
                VEHICLE_ID
        )).thenReturn(expected);

        ResponseEntity<TrackingSessionResponse> response =
                controller.getActiveSession(
                        VEHICLE_ID
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertSame(
                expected,
                response.getBody()
        );

        verify(
                trackingSessionService
        ).getActiveSession(
                USER_ID,
                VEHICLE_ID
        );
    }

    @Test
    void endSessionUsesAuthenticatedUser() {

        Instant endedAt =
                Instant.parse(
                        "2026-09-02T00:00:00Z"
                );

        TrackingSessionResponse expected =
                response(
                        TrackingSessionStatus.ENDED,
                        endedAt,
                        TrackingSessionEndReason.USER_ENDED
                );

        when(trackingSessionService.endSession(
                USER_ID,
                VEHICLE_ID,
                SESSION_ID
        )).thenReturn(expected);

        ResponseEntity<TrackingSessionResponse> response =
                controller.endSession(
                        VEHICLE_ID,
                        SESSION_ID
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertSame(
                expected,
                response.getBody()
        );

        verify(
                trackingSessionService
        ).endSession(
                USER_ID,
                VEHICLE_ID,
                SESSION_ID
        );
    }

    private TrackingSessionResponse response(
            TrackingSessionStatus status,
            Instant endedAt,
            TrackingSessionEndReason endReason
    ) {
        return new TrackingSessionResponse(
                SESSION_ID,
                VEHICLE_ID,
                USER_ID,
                status,
                Instant.parse(
                        "2026-09-02T00:00:00Z"
                ),
                endedAt,
                endReason
        );
    }
}