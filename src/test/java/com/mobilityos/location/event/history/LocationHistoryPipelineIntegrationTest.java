package com.mobilityos.location.event.history;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.entity.VehicleLocationHistory;
import com.mobilityos.location.event.redis.RedisLocationEventConsumer;
import com.mobilityos.location.event.redis.RedisLocationEventPublisher;
import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import com.mobilityos.location.repository.VehicleLocationHistoryRepository;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LocationHistoryPipelineIntegrationTest {

    private static final String STREAM_KEY =
            "location:observations";

    private static final String TEST_PHONE =
            "0911999910";

    private static final String TEST_PLATE =
            "AA-9910";

    @Autowired
    private RedisLocationEventPublisher publisher;

    @Autowired
    private RedisLocationEventConsumer consumer;

    @Autowired
    private LocationHistoryEventHandler handler;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleLocationHistoryRepository historyRepository;

    private Long createdUserId;
    private Long createdOrganizationId;
    private Long createdVehicleId;

    @BeforeEach
    void setUp() {

        /*
         * Ordinary tests run with the automatic history
         * worker disabled. This test drives the Redis
         * consumer explicitly so execution is deterministic.
         */
        redisTemplate.delete(
                STREAM_KEY
        );

        /*
         * Recover cleanly from an earlier interrupted run
         * of this same integration test.
         */
        vehicleRepository
                .findByPlateNumber(TEST_PLATE)
                .ifPresent(vehicle -> {

                    historyRepository
                            .findFirstByVehicleIdOrderByRecordedAtDesc(
                                    vehicle.getId()
                            )
                            .ifPresent(
                                    historyRepository::delete
                            );

                    historyRepository.flush();

                    Long organizationId =
                            vehicle
                                    .getOperator()
                                    .getId();

                    vehicleRepository.delete(
                            vehicle
                    );

                    vehicleRepository.flush();

                    organizationRepository
                            .findById(
                                    organizationId
                            )
                            .ifPresent(
                                    organizationRepository::delete
                            );

                    organizationRepository.flush();
                });

        userRepository
                .findByPhoneNumber(TEST_PHONE)
                .ifPresent(user -> {

                    userRepository.delete(
                            user
                    );

                    userRepository.flush();
                });
    }

    @AfterEach
    void cleanup() {

        redisTemplate.delete(
                STREAM_KEY
        );

        if (createdVehicleId != null) {

            historyRepository
                    .findFirstByVehicleIdOrderByRecordedAtDesc(
                            createdVehicleId
                    )
                    .ifPresent(
                            historyRepository::delete
                    );

            historyRepository.flush();

            vehicleRepository
                    .findById(
                            createdVehicleId
                    )
                    .ifPresent(
                            vehicleRepository::delete
                    );

            vehicleRepository.flush();
        }

        if (createdOrganizationId != null) {

            organizationRepository
                    .findById(
                            createdOrganizationId
                    )
                    .ifPresent(
                            organizationRepository::delete
                    );

            organizationRepository.flush();
        }

        if (createdUserId != null) {

            userRepository
                    .findById(
                            createdUserId
                    )
                    .ifPresent(
                            userRepository::delete
                    );

            userRepository.flush();
        }

        createdVehicleId = null;
        createdOrganizationId = null;
        createdUserId = null;
    }

    @Test
    void redisObservationIsPersistedToPostgresAndAcknowledged() {

        User user =
                userRepository.saveAndFlush(
                        new User(
                                TEST_PHONE,
                                "hash"
                        )
                );

        createdUserId =
                user.getId();

        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "History Integration Test",
                                Organization.OrganizationType.PRIVATE_OWNER
                        )
                );

        createdOrganizationId =
                organization.getId();

        Vehicle vehicle =
                vehicleRepository.saveAndFlush(
                        new Vehicle(
                                organization,
                                TEST_PLATE,
                                Vehicle.VehicleType.MINIBUS,
                                12
                        )
                );

        createdVehicleId =
                vehicle.getId();

        Instant receivedAt =
                Instant.now()
                        .truncatedTo(
                                ChronoUnit.MICROS
                        );

        Instant recordedAt =
                receivedAt.minusSeconds(1);

        LocationObservation observation =
                new LocationObservation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1L,
                        vehicle.getId(),
                        user.getId(),
                        UUID.randomUUID(),
                        9.0300,
                        38.7400,
                        8.5,
                        90.0,
                        8.0,
                        recordedAt,
                        receivedAt,
                        LocationSource.MOBILE
                );

        /*
         * Real Redis XADD.
         */
        publisher.publish(
                observation
        );

        /*
         * Real consumer:
         *
         * Redis Stream
         *     -> decoder
         *     -> transactional history handler
         *     -> PostgreSQL
         *     -> ACK
         */
        int processed =
                consumer.consumeOneBatch(
                        handler
                );

        assertThat(processed)
                .isEqualTo(1);

        VehicleLocationHistory persisted =
                historyRepository
                        .findFirstByVehicleIdOrderByRecordedAtDesc(
                                vehicle.getId()
                        )
                        .orElseThrow();

        assertThat(
                persisted.getVehicle().getId()
        ).isEqualTo(
                vehicle.getId()
        );

        assertThat(
                persisted.getSubmittedByUser().getId()
        ).isEqualTo(
                user.getId()
        );

        assertThat(
                persisted.getLatitude()
        ).isEqualTo(
                observation.latitude()
        );

        assertThat(
                persisted.getLongitude()
        ).isEqualTo(
                observation.longitude()
        );

        assertThat(
                persisted.getSpeed()
        ).isEqualTo(
                observation.speedMetersPerSecond()
        );

        assertThat(
                persisted.getHeading()
        ).isEqualTo(
                observation.headingDegrees()
        );

        assertThat(
                persisted.getAccuracyMeters()
        ).isEqualTo(
                observation.accuracyMeters()
        );

        assertThat(
                persisted.getRecordedAt()
        ).isEqualTo(
                observation.recordedAt()
        );

        /*
         * Important async-history requirement:
         *
         * preserve original HTTP/server receive time,
         * not later worker-processing time.
         */
        assertThat(
                persisted.getReceivedAt()
        ).isEqualTo(
                observation.receivedAt()
        );

        /*
         * XACK proof:
         *
         * If the first processing had remained pending,
         * the consumer's pending-first behavior would return
         * it again here.
         */
        int processedAgain =
                consumer.consumeOneBatch(
                        handler
                );

        assertThat(processedAgain)
                .isZero();
    }

    @Test
    void postgresFailureLeavesRedisObservationPendingForRetry() {

        /*
         * These deliberately nonexistent database IDs make
         * PostgreSQL reject durable history persistence.
         *
         * The observation itself is structurally and
         * quality-valid, so it reaches the history layer.
         */
        long missingVehicleId =
                9_999_999_991L;

        long missingUserId =
                9_999_999_992L;

        Instant receivedAt =
                Instant.now()
                        .truncatedTo(
                                ChronoUnit.MICROS
                        );

        LocationObservation observation =
                new LocationObservation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1L,
                        missingVehicleId,
                        missingUserId,
                        UUID.randomUUID(),
                        9.0300,
                        38.7400,
                        8.5,
                        90.0,
                        8.0,
                        receivedAt.minusSeconds(1),
                        receivedAt,
                        LocationSource.MOBILE
                );

        publisher.publish(
                observation
        );

        /*
         * First processing attempt must fail before ACK.
         */
        assertThatThrownBy(
                () ->
                        consumer.consumeOneBatch(
                                handler
                        )
        ).isInstanceOf(
                RuntimeException.class
        );

        /*
         * The SAME event must still be pending.
         *
         * RedisLocationEventConsumer reads its pending
         * entries before requesting fresh group messages.
         *
         * Therefore the second call should attempt the
         * invalid observation again and fail again.
         *
         * If the first failure had accidentally ACKed the
         * event, this second call would return zero instead.
         */
        assertThatThrownBy(
                () ->
                        consumer.consumeOneBatch(
                                handler
                        )
        ).isInstanceOf(
                RuntimeException.class
        );
    }
}