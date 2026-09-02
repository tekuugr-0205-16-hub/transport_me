package com.mobilityos.location.event.redis;

import com.mobilityos.location.event.LocationEventHandler;
import com.mobilityos.location.observation.LocationObservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class RedisLocationEventConsumer {

    static final String STREAM_KEY =
            "location:observations";

    static final String GROUP_NAME =
            "location-history";

    private static final long BATCH_SIZE =
            100L;

    private final StreamOperations<String, String, String>
            streamOperations;

    private final RedisLocationEventDecoder decoder;

    private final String consumerName;

    private final Object groupMonitor =
            new Object();

    private volatile boolean groupReady;

    /*
     * Spring production constructor.
     *
     * This class has a second constructor for deterministic
     * testing, so we explicitly tell Spring which constructor
     * should be used for dependency injection.
     */
    @Autowired
    public RedisLocationEventConsumer(
            RedisTemplate<String, String> redisTemplate,
            RedisLocationEventDecoder decoder
    ) {
        this(
                redisTemplate,
                decoder,
                "history-" + UUID.randomUUID()
        );
    }

    /*
     * Package-private constructor used by tests so they can
     * supply a stable consumer name.
     */
    RedisLocationEventConsumer(
            RedisTemplate<String, String> redisTemplate,
            RedisLocationEventDecoder decoder,
            String consumerName
    ) {
        Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );

        this.decoder =
                Objects.requireNonNull(
                        decoder,
                        "decoder must not be null"
                );

        if (consumerName == null
                || consumerName.isBlank()) {
            throw new IllegalArgumentException(
                    "consumerName must not be blank"
            );
        }

        this.streamOperations =
                redisTemplate.opsForStream();

        this.consumerName =
                consumerName;
    }

    public int consumeOneBatch(
            LocationEventHandler handler
    ) {
        Objects.requireNonNull(
                handler,
                "handler must not be null"
        );

        List<MapRecord<String, String, String>> records =
                readBatchWithGroupRecovery();

        int processed =
                0;

        for (MapRecord<String, String, String> record
                : records) {

            LocationObservation observation =
                    decoder.decode(record);

            /*
             * ACK must happen only after processing
             * completes successfully.
             *
             * If decoding or handling throws, the Redis
             * record remains pending and can be retried.
             */
            handler.handle(
                    observation
            );

            Long acknowledged =
                    streamOperations.acknowledge(
                            STREAM_KEY,
                            GROUP_NAME,
                            record.getId()
                    );

            if (acknowledged == null
                    || acknowledged.longValue() != 1L) {
                throw new IllegalStateException(
                        "Failed to acknowledge Redis "
                                + "location stream record "
                                + record.getId()
                );
            }

            processed++;
        }

        return processed;
    }

    private List<MapRecord<String, String, String>>
    readBatchWithGroupRecovery() {

        ensureGroup();

        try {
            return readBatch();
        } catch (RuntimeException exception) {

            if (!isNoGroup(exception)) {
                throw exception;
            }

            /*
             * The stream/group may have disappeared after
             * a Redis restart, flush, or stream recreation.
             *
             * Mark the cached state invalid and recreate
             * the group before retrying once.
             */
            groupReady =
                    false;

            ensureGroup();

            return readBatch();
        }
    }

    /*
     * Spring Data Redis exposes stream reads through a
     * parameterized varargs StreamOffset API. All offsets
     * supplied here are explicitly StreamOffset<String>.
     */
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, String, String>>
    readBatch() {

        Consumer consumer =
                Consumer.from(
                        GROUP_NAME,
                        consumerName
                );

        StreamReadOptions options =
                StreamReadOptions
                        .empty()
                        .count(BATCH_SIZE);

        StreamOffset<String> pendingOffset =
                StreamOffset.create(
                        STREAM_KEY,
                        ReadOffset.from(
                                "0-0"
                        )
                );

        /*
         * Retry messages already pending for THIS consumer
         * before asking Redis for new group messages.
         *
         * Recovery of messages owned by a dead different
         * consumer is intentionally a later phase.
         */
        List<MapRecord<String, String, String>> pending =
                streamOperations.read(
                        consumer,
                        options,
                        pendingOffset
                );

        pending =
                safeList(pending);

        if (!pending.isEmpty()) {
            return pending;
        }

        StreamOffset<String> freshOffset =
                StreamOffset.create(
                        STREAM_KEY,
                        ReadOffset.lastConsumed()
                );

        /*
         * No pending messages for this consumer.
         * Now request new messages assigned through the
         * consumer group.
         */
        List<MapRecord<String, String, String>> fresh =
                streamOperations.read(
                        consumer,
                        options,
                        freshOffset
                );

        return safeList(
                fresh
        );
    }

    private void ensureGroup() {

        if (groupReady) {
            return;
        }

        synchronized (groupMonitor) {

            if (groupReady) {
                return;
            }

            try {
                /*
                 * Start at 0-0 so existing stream messages
                 * are eligible for the history consumer.
                 *
                 * Spring Data Redis createGroup also creates
                 * the stream when it does not already exist.
                 */
                streamOperations.createGroup(
                        STREAM_KEY,
                        ReadOffset.from(
                                "0-0"
                        ),
                        GROUP_NAME
                );
            } catch (RuntimeException exception) {

                /*
                 * Multiple application instances may race to
                 * create the same consumer group.
                 *
                 * BUSYGROUP means another instance already
                 * created it, which is a successful state for us.
                 */
                if (!isBusyGroup(exception)) {
                    throw exception;
                }
            }

            groupReady =
                    true;
        }
    }

    private List<MapRecord<String, String, String>>
    safeList(
            List<MapRecord<String, String, String>> records
    ) {
        if (records == null) {
            return List.of();
        }

        return records;
    }

    private boolean isBusyGroup(
            Throwable throwable
    ) {
        return containsRedisError(
                throwable,
                "BUSYGROUP"
        );
    }

    private boolean isNoGroup(
            Throwable throwable
    ) {
        return containsRedisError(
                throwable,
                "NOGROUP"
        );
    }

    private boolean containsRedisError(
            Throwable throwable,
            String token
    ) {
        Throwable current =
                throwable;

        while (current != null) {

            String message =
                    current.getMessage();

            if (message != null
                    && message
                    .toUpperCase(
                            Locale.ROOT
                    )
                    .contains(token)) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }
}