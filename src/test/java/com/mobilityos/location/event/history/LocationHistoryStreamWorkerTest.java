package com.mobilityos.location.event.history;

import com.mobilityos.location.event.redis.RedisLocationEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationHistoryStreamWorkerTest {

    @Mock
    private RedisLocationEventConsumer consumer;

    @Mock
    private LocationHistoryEventHandler handler;

    private LocationHistoryStreamWorker worker;

    @BeforeEach
    void setUp() {
        worker =
                new LocationHistoryStreamWorker(
                        consumer,
                        handler,
                        20
                );
    }

    @Test
    void drainsAvailableBatchesUntilConsumerReturnsZero() {

        when(
                consumer.consumeOneBatch(
                        handler
                )
        ).thenReturn(
                100,
                50,
                0
        );

        worker.drainAvailable();

        verify(
                consumer,
                times(3)
        ).consumeOneBatch(
                handler
        );
    }

    @Test
    void stopsAtConfiguredBatchLimit() {

        LocationHistoryStreamWorker boundedWorker =
                new LocationHistoryStreamWorker(
                        consumer,
                        handler,
                        2
                );

        when(
                consumer.consumeOneBatch(
                        handler
                )
        ).thenReturn(
                100,
                100,
                100
        );

        boundedWorker.drainAvailable();

        verify(
                consumer,
                times(2)
        ).consumeOneBatch(
                handler
        );
    }

    @Test
    void consumerFailureStopsCycleWithoutEscapingSchedulerMethod() {

        when(
                consumer.consumeOneBatch(
                        handler
                )
        ).thenThrow(
                new IllegalStateException(
                        "database unavailable"
                )
        );

        assertDoesNotThrow(
                worker::drainAvailable
        );

        verify(
                consumer,
                times(1)
        ).consumeOneBatch(
                handler
        );
    }

    @Test
    void rejectsNonPositiveBatchLimit() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocationHistoryStreamWorker(
                                consumer,
                                handler,
                                0
                        )
        );
    }
}