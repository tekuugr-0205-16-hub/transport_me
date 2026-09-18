package com.mobilityos.fleet.vehicle;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.fleet.membership.VehicleMember;
import com.mobilityos.fleet.membership.VehicleMemberRepository;
import com.mobilityos.fleet.vehicle.dto.UpdateVehicleStatusRequest;
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
class VehicleStatusTrackingLifecycleIntegrationTest {

    private static final Duration LATCH_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration FUTURE_TIMEOUT =
            Duration.ofSeconds(10);

    /*
     * Long enough to prove that the competing transaction has
     * not completed while the first transaction deliberately
     * retains the vehicle row lock.
     */
    private static final Duration BLOCK_CHECK =
            Duration.ofMillis(300);

    @Autowired
    private VehicleService vehicleService;

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
                                "Vehicle Lifecycle "
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
                                "VT-" + shortToken(),
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
    // TRACKING START WINS VEHICLE LOCK FIRST
    // =========================================================

    @Test
    void deactivationWaitsForTrackingStartAndFinalStateIsDeactivated()
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

        CountDownLatch deactivationAttemptStarted =
                new CountDownLatch(
                        1
                );

        Future<TrackingSessionResponse> startFuture = null;

        Future<Void> deactivationFuture = null;

        try {

            /*
             * Transaction A:
             *
             * startSession() obtains:
             *
             * 1. VehicleMember FOR UPDATE
             * 2. Vehicle FOR UPDATE
             *
             * Keep the outer transaction open after the service
             * returns so the Vehicle lock remains held.
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
             * updateVehicleStatus() must wait on the same
             * Vehicle row lock.
             */
            deactivationFuture =
                    executor.submit(
                            () -> {

                                transactionTemplate.executeWithoutResult(
                                        status -> {

                                            deactivationAttemptStarted
                                                    .countDown();

                                            vehicleService
                                                    .updateVehicleStatus(
                                                            manager.getId(),
                                                            organization.getId(),
                                                            vehicle.getId(),
                                                            new UpdateVehicleStatusRequest(
                                                                    Vehicle.VehicleStatus.INACTIVE
                                                            )
                                                    );
                                        }
                                );

                                return null;
                            }
                    );

            assertTrue(
                    deactivationAttemptStarted.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Vehicle deactivation transaction did not start"
            );

            Thread.sleep(
                    BLOCK_CHECK.toMillis()
            );

            assertFalse(
                    deactivationFuture.isDone(),
                    "Vehicle deactivation should still be waiting while tracking start owns the vehicle lock"
            );

        } finally {

            /*
             * Always release transaction A, even when an
             * assertion fails, so no executor thread is left
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
                deactivationFuture
        ).get(
                FUTURE_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );

        Vehicle persistedVehicle =
                vehicleRepository
                        .findById(
                                vehicle.getId()
                        )
                        .orElseThrow();

        assertEquals(
                Vehicle.VehicleStatus.INACTIVE,
                persistedVehicle.getStatus()
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
                TrackingSessionEndReason.VEHICLE_DEACTIVATED,
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
                "Deactivated vehicle must not retain Redis tracking authority"
        );
    }

    // =========================================================
    // DEACTIVATION WINS VEHICLE LOCK FIRST
    // =========================================================

    @Test
    void trackingStartWaitsForDeactivationAndIsRejectedAfterCommit()
            throws Exception {

        UUID deviceInstallationId =
                UUID.randomUUID();

        CountDownLatch deactivationFinishedInsideTransaction =
                new CountDownLatch(
                        1
                );

        CountDownLatch allowDeactivationToCommit =
                new CountDownLatch(
                        1
                );

        CountDownLatch startAttemptStarted =
                new CountDownLatch(
                        1
                );

        Future<Void> deactivationFuture = null;

        Future<Throwable> startFuture = null;

        try {

            /*
             * Transaction A:
             *
             * Deactivation obtains Vehicle FOR UPDATE, changes the
             * vehicle state, terminates any active session, then
             * deliberately keeps the transaction open.
             */
            deactivationFuture =
                    executor.submit(
                            () -> {

                                transactionTemplate.executeWithoutResult(
                                        status -> {

                                            vehicleService
                                                    .updateVehicleStatus(
                                                            manager.getId(),
                                                            organization.getId(),
                                                            vehicle.getId(),
                                                            new UpdateVehicleStatusRequest(
                                                                    Vehicle.VehicleStatus.INACTIVE
                                                            )
                                                    );

                                            deactivationFinishedInsideTransaction
                                                    .countDown();

                                            awaitLatch(
                                                    allowDeactivationToCommit,
                                                    "Timed out waiting to release vehicle-deactivation transaction"
                                            );
                                        }
                                );

                                return null;
                            }
                    );

            assertTrue(
                    deactivationFinishedInsideTransaction.await(
                            LATCH_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    ),
                    "Vehicle deactivation did not reach the locked transaction state"
            );

            /*
             * Transaction B:
             *
             * startSession() can lock VehicleMember first, but
             * must then wait for the Vehicle row lock.
             */
            startFuture =
                    executor.submit(
                            () -> {

                                startAttemptStarted.countDown();

                                try {

                                    transactionTemplate.execute(
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

                                    return null;

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
                    "Tracking start should still be waiting while deactivation owns the vehicle lock"
            );

        } finally {

            /*
             * Commit INACTIVE state.
             *
             * The waiting tracking transaction should then obtain
             * the Vehicle lock, reread INACTIVE, and reject start.
             */
            allowDeactivationToCommit.countDown();
        }

        Objects.requireNonNull(
                deactivationFuture
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
                "Tracking start unexpectedly succeeded after vehicle deactivation"
        );

        assertInstanceOf(
                ConflictException.class,
                startFailure
        );

        Vehicle persistedVehicle =
                vehicleRepository
                        .findById(
                                vehicle.getId()
                        )
                        .orElseThrow();

        assertEquals(
                Vehicle.VehicleStatus.INACTIVE,
                persistedVehicle.getStatus()
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
         * Redis is outside the PostgreSQL transaction.
         * Defensive cleanup prevents a failed test from leaking
         * runtime state into another test.
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
                    "Thread interrupted while coordinating vehicle lifecycle test",
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
