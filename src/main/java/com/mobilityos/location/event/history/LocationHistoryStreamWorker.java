package com.mobilityos.location.event.history;

import com.mobilityos.location.event.redis.RedisLocationEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "mobilityos.location.history-worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LocationHistoryStreamWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    LocationHistoryStreamWorker.class
            );

    private final RedisLocationEventConsumer consumer;

    private final LocationHistoryEventHandler handler;

    private final int maxBatchesPerCycle;

    public LocationHistoryStreamWorker(
            RedisLocationEventConsumer consumer,
            LocationHistoryEventHandler handler,
            @Value(
                    "${mobilityos.location.history-worker.max-batches-per-cycle:20}"
            )
            int maxBatchesPerCycle
    ) {
        this.consumer =
                Objects.requireNonNull(
                        consumer,
                        "consumer must not be null"
                );

        this.handler =
                Objects.requireNonNull(
                        handler,
                        "handler must not be null"
                );

        if (maxBatchesPerCycle <= 0) {
            throw new IllegalArgumentException(
                    "maxBatchesPerCycle must be positive"
            );
        }

        this.maxBatchesPerCycle =
                maxBatchesPerCycle;
    }

    /*
     * Drain multiple batches during one scheduler cycle.
     *
     * A single Redis consumer read is capped at 100 records.
     * We intentionally allow several reads per cycle so the
     * scheduler itself does not impose a 100-record throughput
     * ceiling.
     *
     * The cycle remains bounded so this worker cannot occupy
     * the scheduler thread forever when the stream is busy.
     */
    @Scheduled(
            fixedDelayString =
                    "${mobilityos.location.history-worker.delay-ms:100}"
    )
    public void drainAvailable() {

        int processedThisCycle =
                0;

        for (int batchNumber = 0;
             batchNumber < maxBatchesPerCycle;
             batchNumber++) {

            final int processed;

            try {
                processed =
                        consumer.consumeOneBatch(
                                handler
                        );
            } catch (RuntimeException exception) {

                /*
                 * RedisLocationEventConsumer ACKs only after
                 * handler success.
                 *
                 * Therefore a history/database failure leaves
                 * the affected stream record pending.
                 *
                 * Stop this cycle and let the next scheduled
                 * cycle retry it.
                 */
                log.warn(
                        "Location history processing failed "
                                + "after {} successful records "
                                + "in this cycle; pending work "
                                + "will be retried",
                        processedThisCycle,
                        exception
                );

                return;
            }

            processedThisCycle +=
                    processed;

            /*
             * Nothing was available. There is no reason to
             * continue polling during this scheduler cycle.
             */
            if (processed == 0) {
                return;
            }
        }

        /*
         * Reaching the bound is not an error. It simply means
         * there may be more work for the next scheduler cycle.
         */
        if (processedThisCycle > 0) {
            log.debug(
                    "Location history worker reached its "
                            + "cycle batch limit after processing "
                            + "{} records",
                    processedThisCycle
            );
        }
    }
}