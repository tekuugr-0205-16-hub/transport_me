package com.mobilityos.location.event.redis;

import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisLocationEventPublisherTest {

    @Autowired
    private RedisLocationEventPublisher publisher;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void cleanup() {
        redisTemplate.delete(
                RedisLocationEventPublisher.STREAM_KEY
        );
    }

    @Test
    void publishesCompleteObservationToRedisStream() {

        LocationObservation observation =
                observation(
                        1L,
                        11.5,
                        90.0
                );

        publisher.publish(observation);

        List<MapRecord<String, String, String>> records =
                records();

        assertThat(records).hasSize(1);

        Map<String, String> fields =
                records.getFirst().getValue();

        assertThat(fields)
                .containsEntry(
                        "observationId",
                        observation.observationId().toString()
                )
                .containsEntry(
                        "trackingSessionId",
                        observation.trackingSessionId().toString()
                )
                .containsEntry(
                        "sequenceNumber",
                        "1"
                )
                .containsEntry(
                        "vehicleId",
                        "101"
                )
                .containsEntry(
                        "submittedByUserId",
                        "201"
                )
                .containsEntry(
                        "deviceInstallationId",
                        observation.deviceInstallationId().toString()
                )
                .containsEntry(
                        "latitude",
                        "9.03"
                )
                .containsEntry(
                        "longitude",
                        "38.74"
                )
                .containsEntry(
                        "speedMetersPerSecond",
                        "11.5"
                )
                .containsEntry(
                        "headingDegrees",
                        "90.0"
                )
                .containsEntry(
                        "accuracyMeters",
                        "8.0"
                )
                .containsEntry(
                        "recordedAt",
                        observation.recordedAt().toString()
                )
                .containsEntry(
                        "receivedAt",
                        observation.receivedAt().toString()
                )
                .containsEntry(
                        "source",
                        "MOBILE"
                );
    }

    @Test
    void omitsOptionalFieldsWhenTheyAreNull() {

        LocationObservation observation =
                observation(
                        1L,
                        null,
                        null
                );

        publisher.publish(observation);

        Map<String, String> fields =
                records()
                        .getFirst()
                        .getValue();

        assertThat(fields)
                .doesNotContainKeys(
                        "speedMetersPerSecond",
                        "headingDegrees"
                );
    }

    @Test
    void retryCanAppendDuplicateForAtLeastOnceDelivery() {

        LocationObservation observation =
                observation(
                        1L,
                        11.5,
                        90.0
                );

        publisher.publish(observation);
        publisher.publish(observation);

        List<MapRecord<String, String, String>> records =
                records();

        assertThat(records).hasSize(2);

        assertThat(
                records.get(0)
                        .getValue()
                        .get("observationId")
        ).isEqualTo(
                observation.observationId().toString()
        );

        assertThat(
                records.get(1)
                        .getValue()
                        .get("observationId")
        ).isEqualTo(
                observation.observationId().toString()
        );
    }

    private List<MapRecord<String, String, String>>
    records() {

        StreamOperations<String, String, String>
                streamOperations =
                redisTemplate.opsForStream();

        return streamOperations.range(
                RedisLocationEventPublisher.STREAM_KEY,
                Range.unbounded()
        );
    }

    private LocationObservation observation(
            long sequenceNumber,
            Double speedMetersPerSecond,
            Double headingDegrees
    ) {

        return new LocationObservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sequenceNumber,
                101L,
                201L,
                UUID.randomUUID(),
                9.03,
                38.74,
                speedMetersPerSecond,
                headingDegrees,
                8.0,
                Instant.parse(
                        "2026-09-02T02:00:00Z"
                ),
                Instant.parse(
                        "2026-09-02T02:00:01Z"
                ),
                LocationSource.MOBILE
        );
    }
}
