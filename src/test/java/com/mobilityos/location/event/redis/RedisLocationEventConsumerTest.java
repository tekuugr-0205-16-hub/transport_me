package com.mobilityos.location.event.redis;

import com.mobilityos.location.event.LocationEventPublisher;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
class RedisLocationEventConsumerTest {

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

    @Autowired
    private RedisTemplate<String, String>
            redisTemplate;

    @Autowired
    private LocationEventPublisher
            publisher;

    @Autowired
    private RedisLocationEventDecoder
            decoder;

    private RedisLocationEventConsumer
            consumer;

    @BeforeEach
    void setUp() {

        redisTemplate.delete(
                RedisLocationEventConsumer.STREAM_KEY
        );

        redisTemplate.delete(
                RedisLocationEventConsumer
                        .DEAD_LETTER_STREAM_KEY
        );

        consumer =
                new RedisLocationEventConsumer(
                        redisTemplate,
                        decoder,
                        "test-history-consumer"
                );
    }

    @AfterEach
    void tearDown() {

        redisTemplate.delete(
                RedisLocationEventConsumer.STREAM_KEY
        );

        redisTemplate.delete(
                RedisLocationEventConsumer
                        .DEAD_LETTER_STREAM_KEY
        );
    }

    @Test
    void publishedObservationIsDecodedHandledAndAcknowledged() {

        LocationObservation expected =
                observation(
                        8.4,
                        125.0
                );

        publisher.publish(
                expected
        );

        List<LocationObservation> handled =
                new ArrayList<>();

        int processed =
                consumer.consumeOneBatch(
                        handled::add
                );

        assertEquals(
                1,
                processed
        );

        assertEquals(
                List.of(expected),
                handled
        );

        /*
         * The record must not be returned again after ACK.
         */
        int secondPoll =
                consumer.consumeOneBatch(
                        observation ->
                                fail(
                                        "Acknowledged event "
                                                + "must not be delivered again"
                                )
                );

        assertEquals(
                0,
                secondPoll
        );

        /*
         * A valid event must never be copied to the DLQ.
         */
        assertEquals(
                0,
                deadLetterRecords().size()
        );
    }

    @Test
    void missingOptionalMotionFieldsDecodeAsNull() {

        LocationObservation expected =
                observation(
                        null,
                        null
                );

        publisher.publish(
                expected
        );

        List<LocationObservation> handled =
                new ArrayList<>();

        int processed =
                consumer.consumeOneBatch(
                        handled::add
                );

        assertEquals(
                1,
                processed
        );

        assertEquals(
                1,
                handled.size()
        );

        assertNull(
                handled.get(0)
                        .speedMetersPerSecond()
        );

        assertNull(
                handled.get(0)
                        .headingDegrees()
        );

        assertEquals(
                expected,
                handled.get(0)
        );

        assertEquals(
                0,
                deadLetterRecords().size()
        );
    }

    @Test
    void handlerFailureLeavesEventPendingAndNextAttemptRetriesIt() {

        LocationObservation expected =
                observation(
                        8.4,
                        125.0
                );

        publisher.publish(
                expected
        );

        /*
         * First processing attempt fails.
         *
         * This simulates a temporary handler/database
         * failure.
         *
         * Consumer must NOT ACK and must NOT DLQ.
         */
        assertThrows(
                IllegalStateException.class,
                () ->
                        consumer.consumeOneBatch(
                                observation -> {
                                    throw new IllegalStateException(
                                            "database unavailable"
                                    );
                                }
                        )
        );

        /*
         * Handler/infrastructure failures are potentially
         * temporary.
         *
         * They must remain retryable rather than being treated
         * as permanently malformed events.
         */
        assertEquals(
                0,
                deadLetterRecords().size(),
                "Temporary handler failure must not be dead-lettered"
        );

        List<LocationObservation> handled =
                new ArrayList<>();

        /*
         * Same consumer must retry its own pending record.
         */
        int retryProcessed =
                consumer.consumeOneBatch(
                        handled::add
                );

        assertEquals(
                1,
                retryProcessed
        );

        assertEquals(
                List.of(expected),
                handled
        );

        /*
         * Successful retry must ACK the record.
         */
        int afterAck =
                consumer.consumeOneBatch(
                        observation ->
                                fail(
                                        "Successfully retried event "
                                                + "must be acknowledged"
                                )
                );

        assertEquals(
                0,
                afterAck
        );

        assertEquals(
                0,
                deadLetterRecords().size()
        );
    }

    @Test
    void stalePendingEventOwnedByDeadConsumerIsReclaimed() {

        LocationObservation expected =
                observation(
                        8.4,
                        125.0
                );

        publisher.publish(
                expected
        );

        /*
         * Simulate application instance A.
         *
         * It receives the record, but handling fails before
         * ACK. The record therefore remains pending and is
         * owned by this consumer.
         */
        RedisLocationEventConsumer deadConsumer =
                new RedisLocationEventConsumer(
                        redisTemplate,
                        decoder,
                        "dead-history-consumer",
                        Duration.ZERO
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        deadConsumer.consumeOneBatch(
                                observation -> {
                                    throw new IllegalStateException(
                                            "consumer instance died"
                                    );
                                }
                        )
        );

        assertEquals(
                0,
                deadLetterRecords().size()
        );

        /*
         * Simulate application instance B.
         *
         * It has a different consumer name.
         *
         * Duration.ZERO is TEST ONLY so the record can be
         * reclaimed immediately instead of waiting for the
         * production 2-minute safety window.
         */
        RedisLocationEventConsumer survivingConsumer =
                new RedisLocationEventConsumer(
                        redisTemplate,
                        decoder,
                        "surviving-history-consumer",
                        Duration.ZERO
                );

        List<LocationObservation> handled =
                new ArrayList<>();

        int recovered =
                survivingConsumer.consumeOneBatch(
                        handled::add
                );

        assertEquals(
                1,
                recovered
        );

        assertEquals(
                List.of(expected),
                handled
        );

        /*
         * The recovered record must be ACKed after successful
         * handling.
         */
        int afterRecovery =
                survivingConsumer.consumeOneBatch(
                        observation ->
                                fail(
                                        "Recovered event must not "
                                                + "remain pending after ACK"
                                )
                );

        assertEquals(
                0,
                afterRecovery
        );

        assertEquals(
                0,
                deadLetterRecords().size()
        );
    }

    @Test
    void malformedEventIsDeadLetteredAndAcknowledged() {

        StreamOperations<String, String, String>
                streamOperations =
                redisTemplate.opsForStream();

        /*
         * Deliberately incomplete payload.
         *
         * observationId is valid, but every other mandatory
         * canonical location field is missing.
         *
         * Retrying this immutable Redis record could never
         * make it valid.
         */
        RecordId originalRecordId =
                streamOperations.add(
                        RedisLocationEventConsumer.STREAM_KEY,
                        Map.of(
                                "observationId",
                                OBSERVATION_ID.toString()
                        )
                );

        assertNotNull(
                originalRecordId
        );

        /*
         * Malformed data must never reach the history handler.
         */
        int processed =
                consumer.consumeOneBatch(
                        observation ->
                                fail(
                                        "Malformed event must never "
                                                + "reach the history handler"
                                )
                );

        assertEquals(
                1,
                processed
        );

        List<MapRecord<String, String, String>> deadLetters =
                deadLetterRecords();

        assertEquals(
                1,
                deadLetters.size()
        );

        Map<String, String> fields =
                deadLetters
                        .getFirst()
                        .getValue();

        /*
         * Original payload is preserved.
         */
        assertEquals(
                OBSERVATION_ID.toString(),
                fields.get(
                        "observationId"
                )
        );

        /*
         * DLQ metadata allows us to identify exactly which
         * original stream record failed.
         */
        assertEquals(
                originalRecordId.toString(),
                fields.get(
                        "_dlqOriginalRecordId"
                )
        );

        assertEquals(
                RedisLocationEventConsumer.STREAM_KEY,
                fields.get(
                        "_dlqOriginalStream"
                )
        );

        assertNotNull(
                fields.get(
                        "_dlqFailedAt"
                )
        );

        assertNotNull(
                fields.get(
                        "_dlqFailureType"
                )
        );

        assertNotNull(
                fields.get(
                        "_dlqFailureMessage"
                )
        );

        /*
         * Once safely preserved in the DLQ, the malformed
         * original must have been acknowledged.
         *
         * Otherwise it would be delivered forever.
         */
        int secondPoll =
                consumer.consumeOneBatch(
                        observation ->
                                fail(
                                        "Dead-lettered original event "
                                                + "must not be delivered again"
                                )
                );

        assertEquals(
                0,
                secondPoll
        );

        /*
         * No duplicate DLQ record should be created by the
         * second poll.
         */
        assertEquals(
                1,
                deadLetterRecords().size()
        );
    }


    private List<MapRecord<String, String, String>>
    deadLetterRecords() {

        StreamOperations<String, String, String>
                streamOperations =
                redisTemplate.opsForStream();

        List<MapRecord<String, String, String>> records =
                streamOperations.range(
                        RedisLocationEventConsumer
                                .DEAD_LETTER_STREAM_KEY,
                        Range.unbounded()
                );

        if (records == null) {
            return List.of();
        }

        return records;
    }

    private LocationObservation observation(
            Double speedMetersPerSecond,
            Double headingDegrees
    ) {
        Instant recordedAt =
                Instant.parse(
                        "2026-09-02T07:00:00Z"
                );

        Instant receivedAt =
                Instant.parse(
                        "2026-09-02T07:00:01Z"
                );

        return new LocationObservation(
                OBSERVATION_ID,
                SESSION_ID,
                847L,
                10L,
                501L,
                DEVICE_ID,
                9.0105,
                38.7612,
                speedMetersPerSecond,
                headingDegrees,
                7.5,
                recordedAt,
                receivedAt,
                LocationSource.MOBILE
        );
    }
}