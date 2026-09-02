package com.mobilityos.location.tracking.runtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisVehicleTrackingRuntimeStore
        implements VehicleTrackingRuntimeStore {

    private static final String KEY_PREFIX =
            "vehicle:tracking:runtime:";

    // =========================================================
    // ACTIVATE NEW SESSION
    // =========================================================

    private static final DefaultRedisScript<Long>
            ACTIVATE_NEW_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local currentSession =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'sessionId'
                        )

                    if currentSession then

                        -- Same-session retry must never reset
                        -- latestSequence back to zero.
                        if currentSession == ARGV[1] then
                            return 0
                        end

                        local currentSecond =
                            tonumber(
                                redis.call(
                                    'HGET',
                                    KEYS[1],
                                    'startedAtEpochSecond'
                                ) or '-1'
                            )

                        local currentNano =
                            tonumber(
                                redis.call(
                                    'HGET',
                                    KEYS[1],
                                    'startedAtNano'
                                ) or '-1'
                            )

                        local incomingSecond =
                            tonumber(ARGV[5])

                        local incomingNano =
                            tonumber(ARGV[6])

                        -- Delayed older activation must never
                        -- overwrite a newer runtime.
                        if currentSecond > incomingSecond then
                            return 0
                        end

                        if currentSecond == incomingSecond
                            and currentNano > incomingNano then

                            return 0
                        end

                        if currentSecond == incomingSecond
                            and currentNano == incomingNano then

                            return 0
                        end
                    end

                    redis.call(
                        'DEL',
                        KEYS[1]
                    )

                    redis.call(
                        'HSET',
                        KEYS[1],

                        'sessionId',
                        ARGV[1],

                        'userId',
                        ARGV[2],

                        'deviceInstallationId',
                        ARGV[3],

                        'latestSequence',
                        '0',

                        'startedAt',
                        ARGV[4],

                        'startedAtEpochSecond',
                        ARGV[5],

                        'startedAtNano',
                        ARGV[6],

                        'lastActivityAt',
                        ARGV[4]
                    )

                    return 1
                    """,
                    Long.class
            );

    // =========================================================
    // ENSURE EXISTING SESSION
    // =========================================================

    private static final DefaultRedisScript<Long>
            ENSURE_ACTIVE_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local currentSession =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'sessionId'
                        )

                    -- Missing runtime means sequence continuity is lost.
                    -- Never recreate an old session with sequence zero.
                    if not currentSession then
                        return 0
                    end

                    if currentSession ~= ARGV[1] then
                        return 2
                    end

                    local currentUser =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'userId'
                        )

                    if not currentUser
                        or currentUser ~= ARGV[2] then

                        return 2
                    end

                    local currentDevice =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'deviceInstallationId'
                        )

                    -- This structural field can safely be repaired
                    -- from the durable PostgreSQL session.
                    if not currentDevice then

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'deviceInstallationId',
                            ARGV[3]
                        )

                    elseif currentDevice ~= ARGV[3] then

                        return 2
                    end

                    -- Sequence is authoritative dynamic state.
                    -- Never invent sequence zero if it disappears.
                    local currentSequence =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'latestSequence'
                        )

                    if not currentSequence then
                        return 0
                    end

                    local currentStartedAt =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'startedAt'
                        )

                    if not currentStartedAt then

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'startedAt',
                            ARGV[4]
                        )
                    end

                    local currentStartedAtEpochSecond =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'startedAtEpochSecond'
                        )

                    if not currentStartedAtEpochSecond then

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'startedAtEpochSecond',
                            ARGV[5]
                        )
                    end

                    local currentStartedAtNano =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'startedAtNano'
                        )

                    if not currentStartedAtNano then

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'startedAtNano',
                            ARGV[6]
                        )
                    end

                    local currentLastActivityAt =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'lastActivityAt'
                        )

                    if not currentLastActivityAt then

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'lastActivityAt',
                            ARGV[4]
                        )
                    end

                    return 1
                    """,
                    Long.class
            );

    // =========================================================
    // DEACTIVATE SESSION
    // =========================================================

    private static final DefaultRedisScript<Long>
            DEACTIVATE_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local currentSession =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'sessionId'
                        )

                    if not currentSession then
                        return 0
                    end

                    if currentSession ~= ARGV[1] then
                        return 0
                    end

                    return redis.call(
                        'DEL',
                        KEYS[1]
                    )
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public RedisVehicleTrackingRuntimeStore(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate =
                redisTemplate;
    }

    // =========================================================
    // ACTIVATE NEW SESSION
    // =========================================================

    @Override
    public boolean activateNewSession(
            Long vehicleId,
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            Instant startedAt
    ) {
        Long result =
                redisTemplate.execute(
                        ACTIVATE_NEW_SESSION_SCRIPT,

                        List.of(
                                key(vehicleId)
                        ),

                        sessionId.toString(),

                        userId.toString(),

                        deviceInstallationId.toString(),

                        startedAt.toString(),

                        Long.toString(
                                startedAt.getEpochSecond()
                        ),

                        Integer.toString(
                                startedAt.getNano()
                        )
                );

        return result != null
                && result == 1L;
    }

    // =========================================================
    // ENSURE EXISTING SESSION
    // =========================================================

    @Override
    public RuntimeEnsureResult ensureActiveSession(
            Long vehicleId,
            UUID sessionId,
            Long userId,
            UUID deviceInstallationId,
            Instant startedAt
    ) {
        Long result =
                redisTemplate.execute(
                        ENSURE_ACTIVE_SESSION_SCRIPT,

                        List.of(
                                key(vehicleId)
                        ),

                        sessionId.toString(),

                        userId.toString(),

                        deviceInstallationId.toString(),

                        startedAt.toString(),

                        Long.toString(
                                startedAt.getEpochSecond()
                        ),

                        Integer.toString(
                                startedAt.getNano()
                        )
                );

        if (result == null) {
            throw new IllegalStateException(
                    "Redis returned no tracking runtime ensure result"
            );
        }

        return switch (result.intValue()) {

            case 0 ->
                    RuntimeEnsureResult.MISSING_RUNTIME;

            case 1 ->
                    RuntimeEnsureResult.ALREADY_ACTIVE;

            case 2 ->
                    RuntimeEnsureResult.CONFLICTING_RUNTIME;

            default ->
                    throw new IllegalStateException(
                            "Unexpected tracking runtime ensure result: "
                                    + result
                    );
        };
    }

    // =========================================================
    // GET RUNTIME
    // =========================================================

    @Override
    public Optional<VehicleTrackingRuntimeState> get(
            Long vehicleId
    ) {
        Map<Object, Object> values =
                redisTemplate
                        .opsForHash()
                        .entries(
                                key(vehicleId)
                        );

        if (values == null
                || values.isEmpty()) {

            return Optional.empty();
        }

        try {
            UUID sessionId =
                    UUID.fromString(
                            required(
                                    values,
                                    "sessionId"
                            )
                    );

            Long userId =
                    Long.valueOf(
                            required(
                                    values,
                                    "userId"
                            )
                    );

            UUID deviceInstallationId =
                    UUID.fromString(
                            required(
                                    values,
                                    "deviceInstallationId"
                            )
                    );

            long latestSequence =
                    Long.parseLong(
                            required(
                                    values,
                                    "latestSequence"
                            )
                    );

            Instant startedAt =
                    Instant.parse(
                            required(
                                    values,
                                    "startedAt"
                            )
                    );

            Instant lastActivityAt =
                    Instant.parse(
                            required(
                                    values,
                                    "lastActivityAt"
                            )
                    );

            return Optional.of(
                    new VehicleTrackingRuntimeState(
                            vehicleId,
                            sessionId,
                            userId,
                            deviceInstallationId,
                            latestSequence,
                            startedAt,
                            lastActivityAt
                    )
            );

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                    "Invalid tracking runtime state for vehicle "
                            + vehicleId,
                    exception
            );
        }
    }

    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    public boolean deactivateIfMatches(
            Long vehicleId,
            UUID sessionId
    ) {
        Long result =
                redisTemplate.execute(
                        DEACTIVATE_IF_MATCHES_SCRIPT,

                        List.of(
                                key(vehicleId)
                        ),

                        sessionId.toString()
                );

        return result != null
                && result > 0;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String key(
            Long vehicleId
    ) {
        return KEY_PREFIX
                + vehicleId;
    }

    private String required(
            Map<Object, Object> values,
            String field
    ) {
        Object value =
                values.get(field);

        if (value == null) {
            throw new IllegalStateException(
                    "Missing Redis tracking field: "
                            + field
            );
        }

        return value.toString();
    }
}