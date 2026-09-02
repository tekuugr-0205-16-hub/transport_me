package com.mobilityos.location.event.redis;

import com.mobilityos.location.event.LocationEventPublisher;
import com.mobilityos.location.observation.LocationObservation;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class RedisLocationEventPublisher
        implements LocationEventPublisher {

    public static final String STREAM_KEY =
            "location:observations";

    private final StreamOperations<String, String, String>
            streamOperations;

    public RedisLocationEventPublisher(
            RedisTemplate<String, String> redisTemplate
    ) {
        Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );

        this.streamOperations =
                redisTemplate.opsForStream();
    }

    @Override
    public void publish(
            LocationObservation observation
    ) {
        Objects.requireNonNull(
                observation,
                "observation must not be null"
        );

        RecordId recordId =
                streamOperations.add(
                        STREAM_KEY,
                        toFields(observation)
                );

        if (recordId == null) {
            throw new IllegalStateException(
                    "Redis did not return a stream record id"
            );
        }
    }

    private Map<String, String> toFields(
            LocationObservation observation
    ) {
        Map<String, String> fields =
                new LinkedHashMap<>();

        fields.put(
                "observationId",
                observation.observationId().toString()
        );

        fields.put(
                "trackingSessionId",
                observation.trackingSessionId().toString()
        );

        fields.put(
                "sequenceNumber",
                Long.toString(
                        observation.sequenceNumber()
                )
        );

        fields.put(
                "vehicleId",
                observation.vehicleId().toString()
        );

        fields.put(
                "submittedByUserId",
                observation.submittedByUserId().toString()
        );

        fields.put(
                "deviceInstallationId",
                observation.deviceInstallationId().toString()
        );

        fields.put(
                "latitude",
                Double.toString(
                        observation.latitude()
                )
        );

        fields.put(
                "longitude",
                Double.toString(
                        observation.longitude()
                )
        );

        if (observation.speedMetersPerSecond() != null) {
            fields.put(
                    "speedMetersPerSecond",
                    Double.toString(
                            observation.speedMetersPerSecond()
                    )
            );
        }

        if (observation.headingDegrees() != null) {
            fields.put(
                    "headingDegrees",
                    Double.toString(
                            observation.headingDegrees()
                    )
            );
        }

        fields.put(
                "accuracyMeters",
                Double.toString(
                        observation.accuracyMeters()
                )
        );

        fields.put(
                "recordedAt",
                observation.recordedAt().toString()
        );

        fields.put(
                "receivedAt",
                observation.receivedAt().toString()
        );

        fields.put(
                "source",
                observation.source().name()
        );

        return fields;
    }
}