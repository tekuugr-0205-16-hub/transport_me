package com.mobilityos.location.event.redis;

import com.mobilityos.location.event.LocationEventPublisher;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
         * If the first event had not been acknowledged,
         * the consumer would find it in its pending
         * entries on this second call.
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

        List<LocationObservation> handled =
                new ArrayList<>();

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