package com.mobilityos.location.live;

import com.mobilityos.location.observation.LocationObservation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisVehicleLiveStateStore
        implements VehicleLiveStateStore {

    private static final String TRACKING_RUNTIME_PREFIX =
            "vehicle:tracking:runtime:";

    private static final String LIVE_STATE_PREFIX =
            "vehicle:live:state:";

    /*
     * Existing shared GEO index used by nearby search.
     */
    private static final String LIVE_GEO_KEY =
            "vehicles:live:geo";

    /*
     * Return codes:
     *
     * -4 = device mismatch
     * -3 = no active runtime
     * -2 = session mismatch
     * -1 = user mismatch
     *  0 = exact current duplicate
     *  1 = stale/conflicting sequence
     *  2 = applied
     *
     * Authorization + sequence validation + live-state update
     * happen atomically inside one Redis Lua script.
     */
    private static final DefaultRedisScript<Long>
            APPLY_OBSERVATION_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local runtimeSession =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'sessionId'
                        )

                    if not runtimeSession then
                        return -3
                    end

                    if runtimeSession ~= ARGV[1] then
                        return -2
                    end

                    local runtimeUser =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'userId'
                        )

                    if not runtimeUser
                        or runtimeUser ~= ARGV[2] then

                        return -1
                    end

                    local runtimeDevice =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'deviceInstallationId'
                        )

                    if not runtimeDevice
                        or runtimeDevice ~= ARGV[3] then

                        return -4
                    end

                    local currentSequence =
                        tonumber(
                            redis.call(
                                'HGET',
                                KEYS[1],
                                'latestSequence'
                            ) or '0'
                        )

                    local incomingSequence =
                        tonumber(ARGV[4])

                    if incomingSequence <= currentSequence then

                        local currentObservationId =
                            redis.call(
                                'HGET',
                                KEYS[1],
                                'latestObservationId'
                            )

                        if incomingSequence == currentSequence
                            and currentObservationId == ARGV[5] then

                            return 0
                        end

                        return 1
                    end

                    -- Longitude MUST come before latitude.
                    redis.call(
                        'GEOADD',
                        KEYS[3],
                        ARGV[7],
                        ARGV[6],
                        ARGV[13]
                    )

                    redis.call(
                        'HSET',
                        KEYS[2],

                        'sessionId',
                        ARGV[1],

                        'userId',
                        ARGV[2],

                        'deviceInstallationId',
                        ARGV[3],

                        'sequenceNumber',
                        ARGV[4],

                        'observationId',
                        ARGV[5],

                        'latitude',
                        ARGV[6],

                        'longitude',
                        ARGV[7],

                        'speedMetersPerSecond',
                        ARGV[8],

                        'headingDegrees',
                        ARGV[9],

                        'accuracyMeters',
                        ARGV[10],

                        'recordedAt',
                        ARGV[11],

                        'receivedAt',
                        ARGV[12]
                    )

                    -- Advance authoritative runtime sequence only
                    -- after the new live state has been written.
                    redis.call(
                        'HSET',
                        KEYS[1],

                        'latestSequence',
                        ARGV[4],

                        'latestObservationId',
                        ARGV[5],

                        'lastActivityAt',
                        ARGV[12]
                    )

                    return 2
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public RedisVehicleLiveStateStore(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate =
                redisTemplate;
    }

    @Override
    public LiveObservationResult applyForLiveState(
            LocationObservation observation
    ) {
        Long result =
                redisTemplate.execute(
                        APPLY_OBSERVATION_SCRIPT,

                        List.of(
                                trackingRuntimeKey(
                                        observation.vehicleId()
                                ),
                                liveStateKey(
                                        observation.vehicleId()
                                ),
                                LIVE_GEO_KEY
                        ),

                        observation
                                .trackingSessionId()
                                .toString(),

                        observation
                                .submittedByUserId()
                                .toString(),

                        observation
                                .deviceInstallationId()
                                .toString(),

                        Long.toString(
                                observation.sequenceNumber()
                        ),

                        observation
                                .observationId()
                                .toString(),

                        Double.toString(
                                observation.latitude()
                        ),

                        Double.toString(
                                observation.longitude()
                        ),

                        nullableDouble(
                                observation
                                        .speedMetersPerSecond()
                        ),

                        nullableDouble(
                                observation
                                        .headingDegrees()
                        ),

                        Double.toString(
                                observation.accuracyMeters()
                        ),

                        observation
                                .recordedAt()
                                .toString(),

                        observation
                                .receivedAt()
                                .toString(),

                        observation
                                .vehicleId()
                                .toString()
                );

        if (result == null) {
            throw new IllegalStateException(
                    "Redis returned no live observation result"
            );
        }

        return switch (result.intValue()) {

            case -4 ->
                    LiveObservationResult.DEVICE_MISMATCH;

            case -3 ->
                    LiveObservationResult.NO_ACTIVE_SESSION;

            case -2 ->
                    LiveObservationResult.SESSION_MISMATCH;

            case -1 ->
                    LiveObservationResult.USER_MISMATCH;

            case 0 ->
                    LiveObservationResult.DUPLICATE_CURRENT;

            case 1 ->
                    LiveObservationResult.STALE_SEQUENCE;

            case 2 ->
                    LiveObservationResult.APPLIED;

            default ->
                    throw new IllegalStateException(
                            "Unexpected Redis live observation result: "
                                    + result
                    );
        };
    }

    private String trackingRuntimeKey(
            Long vehicleId
    ) {
        return TRACKING_RUNTIME_PREFIX
                + vehicleId;
    }

    private String liveStateKey(
            Long vehicleId
    ) {
        return LIVE_STATE_PREFIX
                + vehicleId;
    }

    private String nullableDouble(
            Double value
    ) {
        return value == null
                ? ""
                : Double.toString(value);
    }
}