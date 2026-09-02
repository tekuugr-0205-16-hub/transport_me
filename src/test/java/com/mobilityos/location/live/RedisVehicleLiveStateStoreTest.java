package com.mobilityos.location.live;

import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisVehicleLiveStateStoreTest {

    private static final Long VEHICLE_ID =
            9_900_002L;

    private static final Long USER_ID =
            501L;

    private static final UUID DEVICE_A =
            UUID.fromString(
                    "33333333-3333-4333-8333-333333333333"
            );

    private static final UUID DEVICE_B =
            UUID.fromString(
                    "44444444-4444-4444-8444-444444444444"
            );

    private static final String RUNTIME_KEY =
            "vehicle:tracking:runtime:"
                    + VEHICLE_ID;

    private static final String LIVE_STATE_KEY =
            "vehicle:live:state:"
                    + VEHICLE_ID;

    private static final String GEO_KEY =
            "vehicles:live:geo";

    @Autowired
    private VehicleTrackingRuntimeStore runtimeStore;

    @Autowired
    private VehicleLiveStateStore liveStateStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // =========================================================
    // CLEANUP
    // =========================================================

    @AfterEach
    void cleanup() {

        redisTemplate.delete(
                RUNTIME_KEY
        );

        redisTemplate.delete(
                LIVE_STATE_KEY
        );

        redisTemplate
                .opsForZSet()
                .remove(
                        GEO_KEY,
                        VEHICLE_ID.toString()
                );
    }

    // =========================================================
    // NEW OBSERVATION
    // =========================================================

    @Test
    void newerObservationBecomesLiveState() {

        UUID sessionId =
                activateSession();

        LocationObservation observation =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        LiveObservationResult result =
                liveStateStore
                        .applyForLiveState(
                                observation
                        );

        assertEquals(
                LiveObservationResult.APPLIED,
                result
        );

        assertEquals(
                "1",
                redisTemplate
                        .opsForHash()
                        .get(
                                RUNTIME_KEY,
                                "latestSequence"
                        )
        );

        assertEquals(
                observation
                        .observationId()
                        .toString(),
                redisTemplate
                        .opsForHash()
                        .get(
                                RUNTIME_KEY,
                                "latestObservationId"
                        )
        );

        assertEquals(
                "1",
                redisTemplate
                        .opsForHash()
                        .get(
                                LIVE_STATE_KEY,
                                "sequenceNumber"
                        )
        );

        assertEquals(
                DEVICE_A.toString(),
                redisTemplate
                        .opsForHash()
                        .get(
                                LIVE_STATE_KEY,
                                "deviceInstallationId"
                        )
        );

        List<Point> positions =
                redisTemplate
                        .opsForGeo()
                        .position(
                                GEO_KEY,
                                VEHICLE_ID.toString()
                        );

        assertNotNull(
                positions
        );

        assertEquals(
                1,
                positions.size()
        );

        Point point =
                positions.getFirst();

        assertNotNull(
                point
        );

        /*
         * Spring Point:
         *
         * X = longitude
         * Y = latitude
         */
        assertEquals(
                38.7400,
                point.getX(),
                0.00001
        );

        assertEquals(
                9.0300,
                point.getY(),
                0.00001
        );
    }

    // =========================================================
    // EXACT RETRY
    // =========================================================

    @Test
    void exactRetryIsRecognizedAsDuplicate() {

        UUID sessionId =
                activateSession();

        LocationObservation observation =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        assertEquals(
                LiveObservationResult.APPLIED,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );

        assertEquals(
                LiveObservationResult.DUPLICATE_CURRENT,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );
    }

    // =========================================================
    // STALE SEQUENCE
    // =========================================================

    @Test
    void delayedObservationCannotOverwriteNewerLocation() {

        UUID sessionId =
                activateSession();

        LocationObservation first =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        LocationObservation newer =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        2L,
                        9.0500,
                        38.7600
                );

        LocationObservation delayed =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        1L,
                        8.0000,
                        37.0000
                );

        assertEquals(
                LiveObservationResult.APPLIED,
                liveStateStore
                        .applyForLiveState(
                                first
                        )
        );

        assertEquals(
                LiveObservationResult.APPLIED,
                liveStateStore
                        .applyForLiveState(
                                newer
                        )
        );

        assertEquals(
                LiveObservationResult.STALE_SEQUENCE,
                liveStateStore
                        .applyForLiveState(
                                delayed
                        )
        );

        double storedLatitude =
                Double.parseDouble(
                        redisTemplate
                                .opsForHash()
                                .get(
                                        LIVE_STATE_KEY,
                                        "latitude"
                                )
                                .toString()
                );

        double storedLongitude =
                Double.parseDouble(
                        redisTemplate
                                .opsForHash()
                                .get(
                                        LIVE_STATE_KEY,
                                        "longitude"
                                )
                                .toString()
                );

        assertEquals(
                9.0500,
                storedLatitude,
                0.000001
        );

        assertEquals(
                38.7600,
                storedLongitude,
                0.000001
        );

        assertEquals(
                "2",
                redisTemplate
                        .opsForHash()
                        .get(
                                RUNTIME_KEY,
                                "latestSequence"
                        )
        );
    }

    // =========================================================
    // WRONG SESSION
    // =========================================================

    @Test
    void wrongTrackingSessionCannotUpdateLiveState() {

        activateSession();

        LocationObservation observation =
                observation(
                        UUID.randomUUID(),
                        USER_ID,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        assertEquals(
                LiveObservationResult.SESSION_MISMATCH,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );

        assertFalse(
                redisTemplate
                        .hasKey(
                                LIVE_STATE_KEY
                        )
        );
    }

    // =========================================================
    // WRONG USER
    // =========================================================

    @Test
    void wrongUserCannotUpdateLiveState() {

        UUID sessionId =
                activateSession();

        LocationObservation observation =
                observation(
                        sessionId,
                        999L,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        assertEquals(
                LiveObservationResult.USER_MISMATCH,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );

        assertFalse(
                redisTemplate
                        .hasKey(
                                LIVE_STATE_KEY
                        )
        );
    }

    // =========================================================
    // WRONG DEVICE
    // =========================================================

    @Test
    void wrongDeviceCannotUpdateLiveState() {

        UUID sessionId =
                activateSession();

        /*
         * Same:
         * vehicle
         * session
         * authenticated user
         *
         * Different phone installation.
         */
        LocationObservation observation =
                observation(
                        sessionId,
                        USER_ID,
                        DEVICE_B,
                        1L,
                        9.0300,
                        38.7400
                );

        assertEquals(
                LiveObservationResult.DEVICE_MISMATCH,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );

        assertFalse(
                redisTemplate
                        .hasKey(
                                LIVE_STATE_KEY
                        )
        );

        /*
         * Rejected observation must not advance the
         * authoritative sequence.
         */
        assertEquals(
                "0",
                redisTemplate
                        .opsForHash()
                        .get(
                                RUNTIME_KEY,
                                "latestSequence"
                        )
        );
    }

    // =========================================================
    // NO RUNTIME
    // =========================================================

    @Test
    void observationWithoutRuntimeSessionIsRejected() {

        LocationObservation observation =
                observation(
                        UUID.randomUUID(),
                        USER_ID,
                        DEVICE_A,
                        1L,
                        9.0300,
                        38.7400
                );

        assertEquals(
                LiveObservationResult.NO_ACTIVE_SESSION,
                liveStateStore
                        .applyForLiveState(
                                observation
                        )
        );

        assertFalse(
                redisTemplate
                        .hasKey(
                                LIVE_STATE_KEY
                        )
        );
    }

    // =========================================================
    // RUNTIME HELPER
    // =========================================================

    private UUID activateSession() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        boolean activated =
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        sessionId,
                        USER_ID,
                        DEVICE_A,
                        startedAt
                );

        assertTrue(
                activated,
                "Expected tracking runtime session to activate"
        );

        return sessionId;
    }

    // =========================================================
    // OBSERVATION HELPER
    // =========================================================

    private LocationObservation observation(
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            long sequence,
            double latitude,
            double longitude
    ) {
        Instant now =
                Instant.now();

        return new LocationObservation(
                UUID.randomUUID(),
                sessionId,
                sequence,
                VEHICLE_ID,
                userId,
                deviceInstallationId,
                latitude,
                longitude,
                8.5,
                90.0,
                5.0,
                now.minusSeconds(1),
                now,
                LocationSource.MOBILE
        );
    }
}