package com.mobilityos.location.event.redis;

import com.mobilityos.location.event.LocationEventHandler;
import com.mobilityos.location.observation.LocationObservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RedisLocationEventConsumer {

    static final String STREAM_KEY =
            "location:observations";

    static final String GROUP_NAME =
            "location-history";

    /*
     * Permanently malformed Redis location events are copied
     * here before the original stream record is acknowledged.
     *
     * The DLQ gives us forensic visibility without allowing a
     * poison record to block history processing forever.
     */
    static final String DEAD_LETTER_STREAM_KEY =
            "location:observations:dead-letter";

    private static final long BATCH_SIZE =
            100L;

    private static final int DEAD_LETTER_MESSAGE_MAX_LENGTH =
            1000;

    /*
     * Be conservative in production.
     *
     * A healthy but temporarily slow consumer should not
     * immediately have its work stolen by another instance.
     *
     * Live location does not depend on this history worker,
     * so a 2-minute failover window is acceptable for the
     * durable-history path.
     */
    private static final Duration DEFAULT_PENDING_RECLAIM_IDLE =
            Duration.ofMinutes(2);

    private final StreamOperations<String, String, String>
            streamOperations;

    private final RedisLocationEventDecoder decoder;

    private final String consumerName;

    private final Duration pendingReclaimIdleTime;

    private final Object groupMonitor =
            new Object();

    private volatile boolean groupReady;

    /*
     * Production constructor.
     *
     * Every running application instance receives a unique
     * Redis consumer name.
     */
    @Autowired
    public RedisLocationEventConsumer(
            RedisTemplate<String, String> redisTemplate,
            RedisLocationEventDecoder decoder,
            @Value(
                    "${mobilityos.location.history-consumer.pending-reclaim-idle-ms:120000}"
            )
            long pendingReclaimIdleMillis
    ) {
        this(
                redisTemplate,
                decoder,
                "history-" + UUID.randomUUID(),
                durationFromMillis(
                        pendingReclaimIdleMillis
                )
        );
    }

    /*
     * Package-private constructor retained for existing
     * deterministic tests.
     */
    RedisLocationEventConsumer(
            RedisTemplate<String, String> redisTemplate,
            RedisLocationEventDecoder decoder,
            String consumerName
    ) {
        this(
                redisTemplate,
                decoder,
                consumerName,
                DEFAULT_PENDING_RECLAIM_IDLE
        );
    }

    /*
     * Package-private constructor used by recovery tests.
     *
     * Tests may use Duration.ZERO so they do not need to
     * wait for the production reclaim timeout.
     */
    RedisLocationEventConsumer(
            RedisTemplate<String, String> redisTemplate,
            RedisLocationEventDecoder decoder,
            String consumerName,
            Duration pendingReclaimIdleTime
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

        this.pendingReclaimIdleTime =
                Objects.requireNonNull(
                        pendingReclaimIdleTime,
                        "pendingReclaimIdleTime must not be null"
                );

        if (pendingReclaimIdleTime.isNegative()) {
            throw new IllegalArgumentException(
                    "pendingReclaimIdleTime must not be negative"
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

            LocationObservation observation;

            try {
                observation =
                        decoder.decode(
                                record
                        );

            } catch (LocationEventDecodingException exception) {

                /*
                 * This Redis payload itself is permanently
                 * malformed.
                 *
                 * Retrying the same immutable stream record
                 * cannot repair:
                 *
                 * - missing mandatory fields
                 * - malformed UUIDs
                 * - malformed numbers
                 * - malformed timestamps
                 * - unknown LocationSource values
                 * - invalid LocationObservation values
                 *
                 * Preserve it in the DLQ first.
                 *
                 * Only after the DLQ write succeeds do we ACK
                 * the original record.
                 */
                deadLetterAndAcknowledge(
                        record,
                        exception
                );

                processed++;
                continue;
            }

            /*
             * IMPORTANT:
             *
             * Handler failures are intentionally NOT
             * dead-lettered here.
             *
             * A handler failure may represent:
             *
             * - PostgreSQL temporarily unavailable
             * - connection-pool problem
             * - temporary infrastructure problem
             * - transient transaction failure
             *
             * Those events must remain pending so they can be
             * retried instead of being discarded.
             */
            handler.handle(
                    observation
            );

            /*
             * ACK only after successful handling.
             */
            acknowledge(
                    record
            );

            processed++;
        }

        return processed;
    }

    private void deadLetterAndAcknowledge(
            MapRecord<String, String, String> record,
            LocationEventDecodingException exception
    ) {
        /*
         * Copy the original payload when available.
         *
         * A defensive null check keeps the DLQ path robust
         * even if an unusual MapRecord implementation exposes
         * a null value map.
         */
        Map<String, String> deadLetterFields =
                new LinkedHashMap<>();

        if (record.getValue() != null) {
            deadLetterFields.putAll(
                    record.getValue()
            );
        }

        Throwable cause =
                exception.getCause();

        String failureType =
                cause == null
                        ? exception.getClass().getName()
                        : cause.getClass().getName();

        String failureMessage =
                exception.getMessage();

        if (failureMessage == null
                || failureMessage.isBlank()) {

            failureMessage =
                    failureType;
        }

        /*
         * Do not allow an unexpectedly huge exception message
         * to inflate the Redis DLQ record.
         */
        if (failureMessage.length()
                > DEAD_LETTER_MESSAGE_MAX_LENGTH) {

            failureMessage =
                    failureMessage.substring(
                            0,
                            DEAD_LETTER_MESSAGE_MAX_LENGTH
                    );
        }

        /*
         * Metadata fields use a reserved _dlq prefix so they
         * cannot be confused with the original event schema.
         */
        deadLetterFields.put(
                "_dlqOriginalStream",
                STREAM_KEY
        );

        deadLetterFields.put(
                "_dlqOriginalRecordId",
                record.getId().toString()
        );

        deadLetterFields.put(
                "_dlqFailedAt",
                Instant.now().toString()
        );

        deadLetterFields.put(
                "_dlqFailureType",
                failureType
        );

        deadLetterFields.put(
                "_dlqFailureMessage",
                failureMessage
        );

        /*
         * The DLQ append MUST happen before ACK.
         *
         * If the DLQ write fails:
         *
         * exception propagates
         *      ↓
         * original record is NOT ACKed
         *      ↓
         * original remains pending
         *
         * Therefore malformed data is never silently lost.
         */
        RecordId deadLetterId =
                streamOperations.add(
                        DEAD_LETTER_STREAM_KEY,
                        deadLetterFields
                );

        if (deadLetterId == null) {
            throw new IllegalStateException(
                    "Failed to append malformed location "
                            + "event to dead-letter stream"
            );
        }

        /*
         * The original record can now be safely removed from
         * the consumer group's pending work.
         */
        acknowledge(
                record
        );
    }

    private void acknowledge(
            MapRecord<String, String, String> record
    ) {
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
             * Redis restart, FLUSHDB, stream recreation, or
             * another operation may remove the group after
             * we previously marked it ready.
             *
             * Recreate once and retry.
             */
            groupReady =
                    false;

            ensureGroup();

            return readBatch();
        }
    }

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

        /*
         * =====================================================
         * PRIORITY 1
         *
         * Retry work already pending for THIS consumer.
         * =====================================================
         */
        StreamOffset<String> pendingOffset =
                StreamOffset.create(
                        STREAM_KEY,
                        ReadOffset.from(
                                "0-0"
                        )
                );

        List<MapRecord<String, String, String>> ownPending =
                streamOperations.read(
                        consumer,
                        options,
                        pendingOffset
                );

        ownPending =
                safeList(
                        ownPending
                );

        if (!ownPending.isEmpty()) {
            return ownPending;
        }

        /*
         * =====================================================
         * PRIORITY 2
         *
         * Recover sufficiently old pending work owned by
         * another consumer instance.
         *
         * This covers:
         *
         * instance A:
         *     receives record
         *     crashes before ACK
         *
         * instance B:
         *     later discovers stale pending record
         *     claims ownership
         *     processes it
         *     ACKs it
         * =====================================================
         */
        List<MapRecord<String, String, String>> reclaimed =
                reclaimStalePendingFromOtherConsumers();

        if (!reclaimed.isEmpty()) {
            return reclaimed;
        }

        /*
         * =====================================================
         * PRIORITY 3
         *
         * No unfinished/recoverable work remains.
         * Read fresh group messages.
         * =====================================================
         */
        StreamOffset<String> freshOffset =
                StreamOffset.create(
                        STREAM_KEY,
                        ReadOffset.lastConsumed()
                );

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

    private List<MapRecord<String, String, String>>
    reclaimStalePendingFromOtherConsumers() {

        /*
         * Look across the entire consumer group, but only at
         * entries whose idle time exceeds our safety window.
         *
         * Recovery remains bounded to BATCH_SIZE.
         */
        PendingMessages pendingMessages =
                streamOperations.pending(
                        STREAM_KEY,
                        GROUP_NAME,
                        Range.unbounded(),
                        BATCH_SIZE,
                        pendingReclaimIdleTime
                );

        if (pendingMessages == null
                || pendingMessages.isEmpty()) {

            return List.of();
        }

        List<RecordId> claimableRecordIds =
                new ArrayList<>();

        for (PendingMessage pendingMessage
                : pendingMessages) {

            /*
             * Own pending work was already handled in
             * priority 1.
             *
             * Only reclaim records owned by another consumer.
             */
            if (consumerName.equals(
                    pendingMessage
                            .getConsumer()
                            .getName()
            )) {
                continue;
            }

            claimableRecordIds.add(
                    pendingMessage.getId()
            );
        }

        if (claimableRecordIds.isEmpty()) {
            return List.of();
        }

        RecordId[] recordIds =
                claimableRecordIds.toArray(
                        new RecordId[0]
                );

        /*
         * Redis performs the final idle-time check while
         * transferring ownership with XCLAIM.
         */
        List<MapRecord<String, String, String>> claimed =
                streamOperations.claim(
                        STREAM_KEY,
                        GROUP_NAME,
                        consumerName,
                        pendingReclaimIdleTime,
                        recordIds
                );

        return safeList(
                claimed
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
                 * Start at 0-0 so observations already in the
                 * stream before group creation remain eligible.
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
                 * Multiple application instances may race.
                 *
                 * BUSYGROUP means another instance created the
                 * group first. That is also success for us.
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

    private static Duration durationFromMillis(
            long millis
    ) {
        if (millis < 0L) {
            throw new IllegalArgumentException(
                    "pendingReclaimIdleMillis must not be negative"
            );
        }

        return Duration.ofMillis(
                millis
        );
    }
}