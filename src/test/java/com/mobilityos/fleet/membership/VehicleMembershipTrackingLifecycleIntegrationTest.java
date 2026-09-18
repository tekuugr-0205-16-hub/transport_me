package com.mobilityos.fleet.membership;

import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.TrackingSessionStatus;
import com.mobilityos.location.tracking.VehicleTrackingSession;
import com.mobilityos.location.tracking.VehicleTrackingSessionRepository;
import com.mobilityos.location.tracking.VehicleTrackingSessionService;
import com.mobilityos.location.tracking.dto.TrackingSessionResponse;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.membership.OrganizationMember;
import com.mobilityos.organization.membership.OrganizationMemberRepository;
import com.mobilityos.organization.membership.OrganizationMemberRole;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VehicleMembershipTrackingLifecycleIntegrationTest {

    private static final Duration LATCH_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration FUTURE_TIMEOUT =
            Duration.ofSeconds(10);

    /*
     * Long enough to confirm that the competing request has
     * not completed while the other transaction deliberately
     * retains the VehicleMember row lock.
     */
    private static final Duration BLOCK_CHECK =
            Duration.ofMillis(300);

    @Autowired
    private VehicleMembershipService
            membershipService;

    @Autowired
    private VehicleTrackingSessionService
            trackingSessionService;

    @Autowired
    private VehicleTrackingSessionRepository
            trackingSessionRepository;

    @Autowired
    private VehicleTrackingRuntimeStore
            runtimeStore;

    @Autowired
    private VehicleMemberRepository
            vehicleMemberRepository;

    @Autowired
    private VehicleRepository
            vehicleRepository;

    @Autowired
    private UserRepository
            userRepository;

    @Autowired
    private OrganizationRepository
            organizationRepository;

    @Autowired
    private OrganizationMemberRepository
            organizationMemberRepository;

    @Autowired
    private PlatformTransactionManager
            transactionManager;

    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;

    private User manager;

    private User worker;

    private Organization organization;

    private Vehicle vehicle;

    // =========================================================
    // SETUP
    // =========================================================

    @BeforeEach
    void setUp() {

        transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        executor =
                Executors.newFixedThreadPool(
                        2
                );

        manager =
                userRepository.saveAndFlush(
                        new User(
                                uniquePhoneNumber(),
                                "hash"
                        )
                );

        worker =
                userRepository.saveAndFlush(
                        new User(
                                uniquePhoneNumber(),
                                "hash"
                        )
                );

        organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Lifecycle Integration "
                                        + shortToken(),
                                Organization
                                        .OrganizationType
                                        .PRIVATE_OWNER
                        )
                );

        organizationMemberRepository.saveAndFlush(
                new OrganizationMember(
                        organization,
                        manager,
                        OrganizationMemberRole.OWNER
                )
        );

        vehicle =
                vehicleRepository.saveAndFlush(
                        new Vehicle(
                                organization,
                                "IT-" + shortToken(),
                                Vehicle.VehicleType.MINIBUS,
                                12
                        )
                );

        vehicleMemberRepository.saveAndFlush(
                new VehicleMember(
                        vehicle,
                        worker
                )
        );
    }

    // =========================================================
    // START WINS LOCK FIRST
    // =========================================================

    @Test
    void revocationWaitsForTrackingStartAndFinalStateIsRevoked()
            throws Exception {

        UUID deviceInstallationId =
                UUID.randomUUID();

        CountDownLatch startFinishedInsideTransaction =
                new CountDownLatch(
                        1
                );

        CountDownLatch allowStartTransactionToCommit =
                new CountDownLatch(
                        1
                );

        CountDownLatch revokeAttemptStarted =
                new CountDownLatch(
                        1
                );

        Future<TrackingSessionResponse> startFuture = null;

        Future<Void> revokeFuture = null;

        try {

            /*
             * Transaction A:
             *
             * startSession() acquires the VehicleMember
             * PESSIMISTIC_WRITE lock and creates the tracking
             * session.
             *
             * We intentionally keep the outer transaction open
             * after startSession() returns so the lock remains
             * held.
             */
            startFuture =
                    executor.submit(
                            () ->
                                    transactionTemplate.execute(
                                            status -> {

                                                TrackingSessionResponse response =
                                                        trackingSessionService
                                                                .startSession(
                                                                        worker.getId(),
                                                                        vehicle.getId(),
                                                                        deviceInstallationId
                                                                );

                                                startFinishedInsideTransaction
                                                        .countDown();

                                                awaitLatch(
                                                        allowStartTransactionToCommit,
                                                        "Timed out waiting to release tracking-start transaction"
                                                );

                                                return response;
                                            }
                                    )
                    );

            assertTrue(
                    startFinishedInsideTransaction.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Tracking start did not reach the locked transaction state"
            );

            /*
             * Transaction B:
             *
             * revocation should reach the same VehicleMember row
             * and WAIT because transaction A still owns the lock.
             */
            revokeFuture =
                    executor.submit(
                            () -> {

                                transactionTemplate.executeWithoutResult(
                                        status -> {

                                            revokeAttemptStarted
                                                    .countDown();

                                            membershipService
                                                    .removeVehicleMember(
                                                            manager.getId(),
                                                            vehicle.getId(),
                                                            worker.getId()
                                                    );
                                        }
                                );

                                return null;
                            }
                    );

            assertTrue(
                    revokeAttemptStarted.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Revocation transaction did not start"
            );

            Thread.sleep(
                    BLOCK_CHECK.toMillis()
            );

            assertFalse(
                    revokeFuture.isDone(),
                    "Revocation should still be waiting while tracking start owns the membership lock"
            );

        } finally {

            /*
             * Always release the first transaction, even if an
             * assertion above fails, so no worker thread remains
             * permanently blocked.
             */
            allowStartTransactionToCommit.countDown();
        }

        TrackingSessionResponse startResponse =
                Objects.requireNonNull(
                        startFuture
                ).get(
                        FUTURE_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS
                );

        Objects.requireNonNull(
                revokeFuture
        ).get(
                FUTURE_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );

        /*
         * Revocation is the final lifecycle operation.
         *
         * Regardless of whether the delayed afterCommit
         * activation briefly reached Redis first, final
         * authority must be fully revoked.
         */
        assertFalse(
                vehicleMemberRepository
                        .existsByVehicleIdAndUserId(
                                vehicle.getId(),
                                worker.getId()
                        )
        );

        assertTrue(
                trackingSessionRepository
                        .findByVehicleIdAndStatus(
                                vehicle.getId(),
                                TrackingSessionStatus.ACTIVE
                        )
                        .isEmpty()
        );

        VehicleTrackingSession endedSession =
                trackingSessionRepository
                        .findById(
                                startResponse.sessionId()
                        )
                        .orElseThrow();

        assertEquals(
                TrackingSessionStatus.ENDED,
                endedSession.getStatus()
        );

        assertEquals(
                TrackingSessionEndReason.MEMBERSHIP_REVOKED,
                endedSession.getEndReason()
        );

        assertNotNull(
                endedSession.getEndedAt()
        );

        assertTrue(
                runtimeStore
                        .get(
                                vehicle.getId()
                        )
                        .isEmpty(),
                "Revoked worker must not retain Redis tracking authority"
        );
    }

    // =========================================================
    // REVOCATION WINS LOCK FIRST
    // =========================================================

    @Test
    void trackingStartWaitsForRevocationAndIsRejectedAfterMembershipDeletion()
            throws Exception {

        UUID deviceInstallationId =
                UUID.randomUUID();

        CountDownLatch revokeFinishedInsideTransaction =
                new CountDownLatch(
                        1
                );

        CountDownLatch allowRevocationToCommit =
                new CountDownLatch(
                        1
                );

        CountDownLatch startAttemptStarted =
                new CountDownLatch(
                        1
                );

        Future<Void> revokeFuture = null;

        Future<Throwable> startFuture = null;

        try {

            /*
             * Transaction A:
             *
             * removeVehicleMember() acquires the membership lock,
             * performs the lifecycle revocation, and schedules the
             * VehicleMember deletion.
             *
             * Keep the transaction open before commit.
             */
            revokeFuture =
                    executor.submit(
                            () -> {

                                transactionTemplate.executeWithoutResult(
                                        status -> {

                                            membershipService
                                                    .removeVehicleMember(
                                                            manager.getId(),
                                                            vehicle.getId(),
                                                            worker.getId()
                                                    );

                                            revokeFinishedInsideTransaction
                                                    .countDown();

                                            awaitLatch(
                                                    allowRevocationToCommit,
                                                    "Timed out waiting to release revocation transaction"
                                            );
                                        }
                                );

                                return null;
                            }
                    );

            assertTrue(
                    revokeFinishedInsideTransaction.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Revocation did not reach the locked transaction state"
            );

            /*
             * Transaction B:
             *
             * startSession() should now block on
             * requireVehicleMemberForUpdate().
             */
            startFuture =
                    executor.submit(
                            () -> {

                                startAttemptStarted.countDown();

                                try {

                                    return transactionTemplate.execute(
                                            status -> {

                                                trackingSessionService
                                                        .startSession(
                                                                worker.getId(),
                                                                vehicle.getId(),
                                                                deviceInstallationId
                                                        );

                                                return null;
                                            }
                                    );

                                } catch (Throwable throwable) {

                                    return throwable;
                                }
                            }
                    );

            assertTrue(
                    startAttemptStarted.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Tracking-start transaction did not start"
            );

            Thread.sleep(
                    BLOCK_CHECK.toMillis()
            );

            assertFalse(
                    startFuture.isDone(),
                    "Tracking start should still be waiting while revocation owns the membership lock"
            );

        } finally {

            /*
             * Commit the revocation.
             *
             * The waiting SELECT ... FOR UPDATE should then
             * continue and discover that the membership row no
             * longer exists.
             */
            allowRevocationToCommit.countDown();
        }

        Objects.requireNonNull(
                revokeFuture
        ).get(
                FUTURE_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );

        Throwable startFailure =
                Objects.requireNonNull(
                        startFuture
                ).get(
                        FUTURE_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS
                );

        assertNotNull(
                startFailure,
                "Tracking start unexpectedly succeeded after membership revocation"
        );

        assertInstanceOf(
                ForbiddenException.class,
                startFailure
        );

        assertFalse(
                vehicleMemberRepository
                        .existsByVehicleIdAndUserId(
                                vehicle.getId(),
                                worker.getId()
                        )
        );

        assertTrue(
                trackingSessionRepository
                        .findByVehicleIdAndStatus(
                                vehicle.getId(),
                                TrackingSessionStatus.ACTIVE
                        )
                        .isEmpty()
        );

        assertTrue(
                runtimeStore
                        .get(
                                vehicle.getId()
                        )
                        .isEmpty(),
                "Rejected tracking start must not create Redis authority"
        );
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @AfterEach
    void tearDown() {

        /*
         * Stop test worker threads before deleting database
         * fixtures.
         */
        if (executor != null) {

            executor.shutdownNow();

            try {

                executor.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                );

            } catch (InterruptedException exception) {

                Thread.currentThread()
                        .interrupt();
            }
        }

        /*
         * Redis is not transactional with PostgreSQL.
         *
         * Defensive cleanup ensures a failed test cannot leave a
         * tracking runtime behind in test Redis DB 15.
         */
        if (vehicle != null
                && vehicle.getId() != null) {

            runtimeStore
                    .get(
                            vehicle.getId()
                    )
                    .ifPresent(state ->
                            runtimeStore
                                    .deactivateIfMatches(
                                            vehicle.getId(),
                                            state.sessionId()
                                    )
                    );
        }

        if (transactionTemplate == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(
                status -> cleanupDatabase()
        );
    }

    private void cleanupDatabase() {

        Long vehicleId =
                vehicle != null
                        ? vehicle.getId()
                        : null;

        Long organizationId =
                organization != null
                        ? organization.getId()
                        : null;

        Long managerId =
                manager != null
                        ? manager.getId()
                        : null;

        Long workerId =
                worker != null
                        ? worker.getId()
                        : null;

        if (vehicleId != null) {

            List<VehicleTrackingSession> sessions =
                    trackingSessionRepository
                            .findAll()
                            .stream()
                            .filter(session ->
                                    vehicleId.equals(
                                            session
                                                    .getVehicle()
                                                    .getId()
                                    )
                            )
                            .toList();

            trackingSessionRepository
                    .deleteAll(
                            sessions
                    );

            trackingSessionRepository.flush();

            vehicleMemberRepository
                    .deleteAll(
                            vehicleMemberRepository
                                    .findByVehicleId(
                                            vehicleId
                                    )
                    );

            vehicleMemberRepository.flush();

            if (vehicleRepository
                    .existsById(
                            vehicleId
                    )) {

                vehicleRepository
                        .deleteById(
                                vehicleId
                        );

                vehicleRepository.flush();
            }
        }

        if (organizationId != null) {

            organizationMemberRepository
                    .deleteAll(
                            organizationMemberRepository
                                    .findByOrganizationId(
                                            organizationId
                                    )
                    );

            organizationMemberRepository.flush();

            if (organizationRepository
                    .existsById(
                            organizationId
                    )) {

                organizationRepository
                        .deleteById(
                                organizationId
                        );

                organizationRepository.flush();
            }
        }

        if (managerId != null
                && userRepository.existsById(
                managerId
        )) {

            userRepository.deleteById(
                    managerId
            );
        }

        if (workerId != null
                && userRepository.existsById(
                workerId
        )) {

            userRepository.deleteById(
                    workerId
            );
        }

        userRepository.flush();
    }

    // =========================================================
    // CONCURRENCY HELPERS
    // =========================================================

    private static void awaitLatch(
            CountDownLatch latch,
            String timeoutMessage
    ) {

        try {

            boolean completed =
                    latch.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    );

            if (!completed) {

                throw new AssertionError(
                        timeoutMessage
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            throw new AssertionError(
                    "Thread interrupted while coordinating lifecycle test",
                    exception
            );
        }
    }

    // =========================================================
    // TEST DATA
    // =========================================================

    private static String uniquePhoneNumber() {

        int suffix =
                Math.floorMod(
                        UUID.randomUUID()
                                .hashCode(),
                        100_000_000
                );

        return String.format(
                Locale.ROOT,
                "09%08d",
                suffix
        );
    }

    private static String shortToken() {

        return UUID.randomUUID()
                .toString()
                .substring(
                        0,
                        8
                )
                .toUpperCase(
                        Locale.ROOT
                );
    }
}