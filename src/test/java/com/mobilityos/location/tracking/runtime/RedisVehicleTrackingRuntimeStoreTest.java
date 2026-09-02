package com.mobilityos.location.tracking.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisVehicleTrackingRuntimeStoreTest {

    private static final Long VEHICLE_ID =
            9_900_001L;

    private static final String KEY =
            "vehicle:tracking:runtime:"
                    + VEHICLE_ID;

    private static final UUID DEVICE_A =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID DEVICE_B =
            UUID.fromString(
                    "22222222-2222-4222-8222-222222222222"
            );

    @Autowired
    private VehicleTrackingRuntimeStore runtimeStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // =========================================================
    // CLEANUP
    // =========================================================

    @AfterEach
    void cleanup() {

        redisTemplate.delete(
                KEY
        );
    }

    // =========================================================
    // NEW SESSION
    // =========================================================

    @Test
    void activatesAndReadsNewRuntimeSession() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        boolean activated =
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                );

        assertTrue(
                activated
        );

        VehicleTrackingRuntimeState state =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                VEHICLE_ID,
                state.vehicleId()
        );

        assertEquals(
                sessionId,
                state.sessionId()
        );

        assertEquals(
                101L,
                state.userId()
        );

        assertEquals(
                DEVICE_A,
                state.deviceInstallationId()
        );

        assertEquals(
                0L,
                state.latestSequence()
        );

        assertEquals(
                startedAt,
                state.startedAt()
        );

        assertEquals(
                startedAt,
                state.lastActivityAt()
        );
    }

    // =========================================================
    // HEALTHY SAME SESSION
    // =========================================================

    @Test
    void ensureSameSessionPreservesDynamicGpsState() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                )
        );

        UUID observationId =
                UUID.randomUUID();

        Instant recentActivity =
                startedAt.plusSeconds(30);

        redisTemplate
                .opsForHash()
                .put(
                        KEY,
                        "latestSequence",
                        "77"
                );

        redisTemplate
                .opsForHash()
                .put(
                        KEY,
                        "latestObservationId",
                        observationId.toString()
                );

        redisTemplate
                .opsForHash()
                .put(
                        KEY,
                        "lastActivityAt",
                        recentActivity.toString()
                );

        RuntimeEnsureResult result =
                runtimeStore.ensureActiveSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                );

        assertEquals(
                RuntimeEnsureResult.ALREADY_ACTIVE,
                result
        );

        assertEquals(
                "77",
                redisTemplate
                        .opsForHash()
                        .get(
                                KEY,
                                "latestSequence"
                        )
        );

        assertEquals(
                observationId.toString(),
                redisTemplate
                        .opsForHash()
                        .get(
                                KEY,
                                "latestObservationId"
                        )
        );

        assertEquals(
                recentActivity.toString(),
                redisTemplate
                        .opsForHash()
                        .get(
                                KEY,
                                "lastActivityAt"
                        )
        );

        VehicleTrackingRuntimeState state =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                DEVICE_A,
                state.deviceInstallationId()
        );

        assertEquals(
                77L,
                state.latestSequence()
        );
    }

    // =========================================================
    // COMPLETE REDIS LOSS
    // =========================================================

    @Test
    void missingRuntimeRequiresNewTrackingSession() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        assertFalse(
                redisTemplate.hasKey(
                        KEY
                )
        );

        RuntimeEnsureResult result =
                runtimeStore.ensureActiveSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                );

        assertEquals(
                RuntimeEnsureResult.MISSING_RUNTIME,
                result
        );

        /*
         * Critical rule:
         *
         * ensureActiveSession must NOT recreate the old
         * PostgreSQL tracking session with sequence zero.
         */
        assertFalse(
                redisTemplate.hasKey(
                        KEY
                )
        );

        assertTrue(
                runtimeStore
                        .get(VEHICLE_ID)
                        .isEmpty()
        );
    }

    // =========================================================
    // LOST SEQUENCE FIELD
    // =========================================================

    @Test
    void missingLatestSequenceRequiresNewTrackingSession() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                )
        );

        redisTemplate
                .opsForHash()
                .delete(
                        KEY,
                        "latestSequence"
                );

        assertFalse(
                redisTemplate
                        .opsForHash()
                        .hasKey(
                                KEY,
                                "latestSequence"
                        )
        );

        RuntimeEnsureResult result =
                runtimeStore.ensureActiveSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                );

        assertEquals(
                RuntimeEnsureResult.MISSING_RUNTIME,
                result
        );

        /*
         * Never invent sequence zero.
         */
        assertFalse(
                redisTemplate
                        .opsForHash()
                        .hasKey(
                                KEY,
                                "latestSequence"
                        )
        );
    }

    // =========================================================
    // DIFFERENT SESSION
    // =========================================================

    @Test
    void ensureDifferentSessionDoesNotOverwriteRuntime() {

        UUID activeSession =
                UUID.randomUUID();

        UUID conflictingSession =
                UUID.randomUUID();

        Instant activeStartedAt =
                Instant.now();

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        activeSession,
                        101L,
                        DEVICE_A,
                        activeStartedAt
                )
        );

        RuntimeEnsureResult result =
                runtimeStore.ensureActiveSession(
                        VEHICLE_ID,
                        conflictingSession,
                        101L,
                        DEVICE_A,
                        activeStartedAt.plusSeconds(10)
                );

        assertEquals(
                RuntimeEnsureResult.CONFLICTING_RUNTIME,
                result
        );

        VehicleTrackingRuntimeState state =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                activeSession,
                state.sessionId()
        );

        assertEquals(
                101L,
                state.userId()
        );

        assertEquals(
                DEVICE_A,
                state.deviceInstallationId()
        );
    }

    // =========================================================
    // DIFFERENT DEVICE
    // =========================================================

    @Test
    void differentDeviceCannotRepairSameRuntimeSession() {

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_A,
                        startedAt
                )
        );

        RuntimeEnsureResult result =
                runtimeStore.ensureActiveSession(
                        VEHICLE_ID,
                        sessionId,
                        101L,
                        DEVICE_B,
                        startedAt
                );

        assertEquals(
                RuntimeEnsureResult.CONFLICTING_RUNTIME,
                result
        );

        VehicleTrackingRuntimeState state =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                sessionId,
                state.sessionId()
        );

        assertEquals(
                101L,
                state.userId()
        );

        assertEquals(
                DEVICE_A,
                state.deviceInstallationId()
        );
    }

    // =========================================================
    // DELAYED OLD ACTIVATION
    // =========================================================

    @Test
    void olderActivationCannotOverwriteNewerRuntime() {

        UUID olderSession =
                UUID.randomUUID();

        UUID newerSession =
                UUID.randomUUID();

        Instant olderStartedAt =
                Instant.now();

        Instant newerStartedAt =
                olderStartedAt.plusSeconds(10);

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        newerSession,
                        202L,
                        DEVICE_B,
                        newerStartedAt
                )
        );

        boolean oldActivationAccepted =
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        olderSession,
                        101L,
                        DEVICE_A,
                        olderStartedAt
                );

        assertFalse(
                oldActivationAccepted
        );

        VehicleTrackingRuntimeState state =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                newerSession,
                state.sessionId()
        );

        assertEquals(
                202L,
                state.userId()
        );

        assertEquals(
                DEVICE_B,
                state.deviceInstallationId()
        );

        assertEquals(
                newerStartedAt,
                state.startedAt()
        );
    }

    // =========================================================
    // SAFE DEACTIVATION
    // =========================================================

    @Test
    void oldSessionCannotDeleteNewerRuntimeState() {

        UUID activeSession =
                UUID.randomUUID();

        UUID staleSession =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

        assertTrue(
                runtimeStore.activateNewSession(
                        VEHICLE_ID,
                        activeSession,
                        101L,
                        DEVICE_A,
                        startedAt
                )
        );

        boolean staleDelete =
                runtimeStore.deactivateIfMatches(
                        VEHICLE_ID,
                        staleSession
                );

        assertFalse(
                staleDelete
        );

        VehicleTrackingRuntimeState stillActive =
                runtimeStore
                        .get(VEHICLE_ID)
                        .orElseThrow();

        assertEquals(
                activeSession,
                stillActive.sessionId()
        );

        assertEquals(
                DEVICE_A,
                stillActive.deviceInstallationId()
        );

        boolean correctDelete =
                runtimeStore.deactivateIfMatches(
                        VEHICLE_ID,
                        activeSession
                );

        assertTrue(
                correctDelete
        );

        assertTrue(
                runtimeStore
                        .get(VEHICLE_ID)
                        .isEmpty()
        );
    }
}