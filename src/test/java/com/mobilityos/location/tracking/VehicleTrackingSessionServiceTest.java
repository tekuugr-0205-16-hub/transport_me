package com.mobilityos.location.tracking;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.tracking.dto.TrackingSessionResponse;
import com.mobilityos.location.tracking.runtime.RuntimeEnsureResult;
import com.mobilityos.location.tracking.runtime.VehicleTrackingRuntimeStore;
import com.mobilityos.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VehicleTrackingSessionServiceTest {

    private static final UUID DEVICE_A =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID DEVICE_B =
            UUID.fromString(
                    "22222222-2222-4222-8222-222222222222"
            );

    // =========================================================
    // CREATE NEW SESSION
    // =========================================================

    @Test
    void startsNewSessionForDevice()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByUserIdAndStatus(
                        1L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByDeviceInstallationIdAndStatus(
                        DEVICE_A,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.userRepository.findById(1L))
                .thenReturn(
                        Optional.of(
                                fixture.user
                        )
                );

        when(fixture.trackingSessionRepository
                .saveAndFlush(
                        any(VehicleTrackingSession.class)
                ))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        TrackingSessionResponse response =
                fixture.service.startSession(
                        1L,
                        10L,
                        DEVICE_A
                );

        assertNotNull(
                response.sessionId()
        );

        assertEquals(
                TrackingSessionStatus.ACTIVE,
                response.status()
        );

        assertEquals(
                10L,
                response.vehicleId()
        );

        assertEquals(
                1L,
                response.userId()
        );

        verify(
                fixture.trackingSessionRepository
        ).saveAndFlush(
                argThat(session ->
                        DEVICE_A.equals(
                                session.getDeviceInstallationId()
                        )
                )
        );

        verify(
                fixture.runtimeStore
        ).activateNewSession(
                eq(10L),
                eq(response.sessionId()),
                eq(1L),
                eq(DEVICE_A),
                any(Instant.class)
        );
    }

    // =========================================================
    // SAME DEVICE RESUME
    // =========================================================

    @Test
    void sameDeviceCanResumeExistingSession()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        when(fixture.runtimeStore
                .ensureActiveSession(
                        10L,
                        existing.getId(),
                        1L,
                        DEVICE_A,
                        existing.getStartedAt()
                ))
                .thenReturn(
                        RuntimeEnsureResult.ALREADY_ACTIVE
                );

        TrackingSessionResponse response =
                fixture.service.startSession(
                        1L,
                        10L,
                        DEVICE_A
                );

        assertEquals(
                existing.getId(),
                response.sessionId()
        );

        verify(
                fixture.runtimeStore
        ).ensureActiveSession(
                10L,
                existing.getId(),
                1L,
                DEVICE_A,
                existing.getStartedAt()
        );

        verify(
                fixture.trackingSessionRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }

    // =========================================================
    // RUNTIME LOSS RECOVERY
    // =========================================================

    @Test
    void missingRuntimeRotatesSessionInsteadOfResettingSequence()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        UUID oldSessionId =
                existing.getId();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        when(fixture.runtimeStore
                .ensureActiveSession(
                        10L,
                        oldSessionId,
                        1L,
                        DEVICE_A,
                        existing.getStartedAt()
                ))
                .thenReturn(
                        RuntimeEnsureResult.MISSING_RUNTIME
                );

        when(fixture.trackingSessionRepository
                .saveAndFlush(
                        any(VehicleTrackingSession.class)
                ))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        TrackingSessionResponse response =
                fixture.service.startSession(
                        1L,
                        10L,
                        DEVICE_A
                );

        assertNotNull(
                response.sessionId()
        );

        assertNotEquals(
                oldSessionId,
                response.sessionId()
        );

        assertEquals(
                TrackingSessionStatus.ACTIVE,
                response.status()
        );

        assertEquals(
                10L,
                response.vehicleId()
        );

        assertEquals(
                1L,
                response.userId()
        );

        assertEquals(
                TrackingSessionStatus.ENDED,
                existing.getStatus()
        );

        assertEquals(
                TrackingSessionEndReason.SESSION_TIMEOUT,
                existing.getEndReason()
        );

        assertNotNull(
                existing.getEndedAt()
        );

        verify(
                fixture.trackingSessionRepository
        ).saveAndFlush(
                argThat(session ->
                        session == existing
                                && session.getStatus()
                                == TrackingSessionStatus.ENDED
                                && session.getEndReason()
                                == TrackingSessionEndReason.SESSION_TIMEOUT
                )
        );

        verify(
                fixture.trackingSessionRepository
        ).saveAndFlush(
                argThat(session ->
                        session != existing
                                && session.getStatus()
                                == TrackingSessionStatus.ACTIVE
                                && DEVICE_A.equals(
                                session.getDeviceInstallationId()
                        )
                )
        );

        verify(
                fixture.runtimeStore
        ).deactivateIfMatches(
                10L,
                oldSessionId
        );

        verify(
                fixture.runtimeStore
        ).activateNewSession(
                eq(10L),
                eq(response.sessionId()),
                eq(1L),
                eq(DEVICE_A),
                any(Instant.class)
        );
    }

    // =========================================================
    // DIFFERENT PHONE
    // =========================================================

    @Test
    void differentDeviceCannotSilentlyResumeExistingSession()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_B
                        )
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );

        verify(
                fixture.trackingSessionRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }

    // =========================================================
    // VEHICLE ALREADY OPERATED
    // =========================================================

    @Test
    void rejectsVehicleOperatedByAnotherUser()
            throws Exception {

        Fixture fixture = fixture();

        User otherUser =
                user(
                        2L,
                        "0922000000"
                );

        VehicleTrackingSession existing =
                new VehicleTrackingSession(
                        fixture.vehicle,
                        otherUser,
                        DEVICE_B
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_A
                        )
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );
    }

    // =========================================================
    // USER OPERATING ANOTHER VEHICLE
    // =========================================================

    @Test
    void rejectsUserOperatingAnotherVehicle()
            throws Exception {

        Fixture fixture = fixture();

        Vehicle secondVehicle =
                vehicle(
                        20L,
                        fixture.organization,
                        "AA-2000"
                );

        VehicleTrackingSession existing =
                new VehicleTrackingSession(
                        secondVehicle,
                        fixture.user,
                        DEVICE_A
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByUserIdAndStatus(
                        1L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_A
                        )
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );
    }

    // =========================================================
    // DEVICE ALREADY ACTIVE
    // =========================================================

    @Test
    void rejectsDeviceAlreadyRunningAnotherSession()
            throws Exception {

        Fixture fixture = fixture();

        User otherUser =
                user(
                        2L,
                        "0922000000"
                );

        Vehicle otherVehicle =
                vehicle(
                        20L,
                        fixture.organization,
                        "AA-2000"
                );

        VehicleTrackingSession deviceSession =
                new VehicleTrackingSession(
                        otherVehicle,
                        otherUser,
                        DEVICE_A
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByUserIdAndStatus(
                        1L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByDeviceInstallationIdAndStatus(
                        DEVICE_A,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                deviceSession
                        )
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_A
                        )
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );

        verify(
                fixture.trackingSessionRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }

    // =========================================================
    // DATABASE CONCURRENCY
    // =========================================================

    @Test
    void concurrentDatabaseConflictBecomesDomainConflict()
            throws Exception {

        Fixture fixture = fixture();

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByUserIdAndStatus(
                        1L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.trackingSessionRepository
                .findByDeviceInstallationIdAndStatus(
                        DEVICE_A,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.empty()
                );

        when(fixture.userRepository.findById(1L))
                .thenReturn(
                        Optional.of(
                                fixture.user
                        )
                );

        when(fixture.trackingSessionRepository
                .saveAndFlush(
                        any(VehicleTrackingSession.class)
                ))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Concurrent active session"
                        )
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_A
                        )
        );

        verifyNoInteractions(
                fixture.runtimeStore
        );
    }

    // =========================================================
    // GET ACTIVE SESSION
    // =========================================================

    @Test
    void getActiveSessionReturnsHealthyRuntime()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        when(fixture.runtimeStore
                .ensureActiveSession(
                        10L,
                        existing.getId(),
                        1L,
                        DEVICE_A,
                        existing.getStartedAt()
                ))
                .thenReturn(
                        RuntimeEnsureResult.ALREADY_ACTIVE
                );

        TrackingSessionResponse response =
                fixture.service.getActiveSession(
                        1L,
                        10L
                );

        assertEquals(
                existing.getId(),
                response.sessionId()
        );

        verify(
                fixture.fleetAccessService
        ).requireVehicleMember(
                1L,
                10L
        );

        verify(
                fixture.runtimeStore
        ).ensureActiveSession(
                10L,
                existing.getId(),
                1L,
                DEVICE_A,
                existing.getStartedAt()
        );
    }

    // =========================================================
    // GET ACTIVE AFTER RUNTIME LOSS
    // =========================================================

    @Test
    void getActiveSessionDoesNotRebuildMissingRuntime()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        when(fixture.runtimeStore
                .ensureActiveSession(
                        10L,
                        existing.getId(),
                        1L,
                        DEVICE_A,
                        existing.getStartedAt()
                ))
                .thenReturn(
                        RuntimeEnsureResult.MISSING_RUNTIME
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.getActiveSession(
                                1L,
                                10L
                        )
        );

        verify(
                fixture.runtimeStore
        ).ensureActiveSession(
                10L,
                existing.getId(),
                1L,
                DEVICE_A,
                existing.getStartedAt()
        );

        verify(
                fixture.runtimeStore,
                never()
        ).activateNewSession(
                anyLong(),
                any(UUID.class),
                anyLong(),
                any(UUID.class),
                any(Instant.class)
        );

        verify(
                fixture.trackingSessionRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }

    // =========================================================
    // CONFLICTING REDIS RUNTIME
    // =========================================================

    @Test
    void conflictingRuntimeIsNotOverwritten()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.vehicleRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                fixture.vehicle
                        )
                );

        when(fixture.trackingSessionRepository
                .findByVehicleIdAndStatus(
                        10L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        when(fixture.runtimeStore
                .ensureActiveSession(
                        10L,
                        existing.getId(),
                        1L,
                        DEVICE_A,
                        existing.getStartedAt()
                ))
                .thenReturn(
                        RuntimeEnsureResult.CONFLICTING_RUNTIME
                );

        assertThrows(
                ConflictException.class,
                () ->
                        fixture.service.startSession(
                                1L,
                                10L,
                                DEVICE_A
                        )
        );

        verify(
                fixture.runtimeStore,
                never()
        ).activateNewSession(
                anyLong(),
                any(UUID.class),
                anyLong(),
                any(UUID.class),
                any(Instant.class)
        );
    }

    // =========================================================
    // USER END
    // =========================================================

    @Test
    void userCanEndOwnSessionIdempotently()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        UUID sessionId =
                existing.getId();

        when(fixture.trackingSessionRepository
                .findByIdAndVehicleIdAndUserId(
                        sessionId,
                        10L,
                        1L
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        TrackingSessionResponse first =
                fixture.service.endSession(
                        1L,
                        10L,
                        sessionId
                );

        assertEquals(
                TrackingSessionStatus.ENDED,
                first.status()
        );

        assertEquals(
                TrackingSessionEndReason.USER_ENDED,
                first.endReason()
        );

        assertNotNull(
                first.endedAt()
        );

        Instant firstEndedAt =
                first.endedAt();

        TrackingSessionResponse second =
                fixture.service.endSession(
                        1L,
                        10L,
                        sessionId
                );

        assertEquals(
                TrackingSessionStatus.ENDED,
                second.status()
        );

        assertEquals(
                TrackingSessionEndReason.USER_ENDED,
                second.endReason()
        );

        assertEquals(
                firstEndedAt,
                second.endedAt()
        );

        verify(
                fixture.runtimeStore,
                times(2)
        ).deactivateIfMatches(
                10L,
                sessionId
        );
    }

    // =========================================================
    // INTERNAL TERMINATION
    // =========================================================

    @Test
    void internalTerminationEndsAndInvalidatesSession()
            throws Exception {

        Fixture fixture = fixture();

        VehicleTrackingSession existing =
                session(
                        fixture,
                        DEVICE_A
                );

        when(fixture.trackingSessionRepository
                .findByUserIdAndStatus(
                        1L,
                        TrackingSessionStatus.ACTIVE
                ))
                .thenReturn(
                        Optional.of(
                                existing
                        )
                );

        fixture.service.terminateActiveSession(
                10L,
                1L,
                TrackingSessionEndReason.MEMBERSHIP_REVOKED
        );

        assertEquals(
                TrackingSessionStatus.ENDED,
                existing.getStatus()
        );

        assertEquals(
                TrackingSessionEndReason.MEMBERSHIP_REVOKED,
                existing.getEndReason()
        );

        assertNotNull(
                existing.getEndedAt()
        );

        verify(
                fixture.runtimeStore
        ).deactivateIfMatches(
                10L,
                existing.getId()
        );
    }

    // =========================================================
    // SESSION HELPER
    // =========================================================

    private static VehicleTrackingSession session(
            Fixture fixture,
            UUID deviceId
    ) {
        return new VehicleTrackingSession(
                fixture.vehicle,
                fixture.user,
                deviceId
        );
    }

    // =========================================================
    // FIXTURE
    // =========================================================

    private static Fixture fixture()
            throws Exception {

        VehicleTrackingSessionRepository trackingSessionRepository =
                mock(
                        VehicleTrackingSessionRepository.class
                );

        VehicleRepository vehicleRepository =
                mock(
                        VehicleRepository.class
                );

        UserRepository userRepository =
                mock(
                        UserRepository.class
                );

        FleetAccessService fleetAccessService =
                mock(
                        FleetAccessService.class
                );

        VehicleTrackingRuntimeStore runtimeStore =
                mock(
                        VehicleTrackingRuntimeStore.class
                );

        VehicleTrackingSessionService service =
                new VehicleTrackingSessionService(
                        trackingSessionRepository,
                        vehicleRepository,
                        userRepository,
                        fleetAccessService,
                        runtimeStore
                );

        User user =
                user(
                        1L,
                        "0911000000"
                );

        Organization organization =
                organization(
                        100L,
                        "Test Transport"
                );

        Vehicle vehicle =
                vehicle(
                        10L,
                        organization,
                        "AA-1000"
                );

        return new Fixture(
                service,
                trackingSessionRepository,
                vehicleRepository,
                userRepository,
                fleetAccessService,
                runtimeStore,
                user,
                organization,
                vehicle
        );
    }

    // =========================================================
    // ENTITY HELPERS
    // =========================================================

    private static User user(
            Long id,
            String phone
    ) throws Exception {

        User user =
                new User(
                        phone,
                        "hash"
                );

        setField(
                User.class,
                user,
                "id",
                id
        );

        return user;
    }

    private static Organization organization(
            Long id,
            String name
    ) throws Exception {

        Organization organization =
                new Organization(
                        name,
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        setField(
                Organization.class,
                organization,
                "id",
                id
        );

        return organization;
    }

    private static Vehicle vehicle(
            Long id,
            Organization organization,
            String plate
    ) throws Exception {

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        plate,
                        Vehicle.VehicleType.MINIBUS,
                        12
                );

        setField(
                Vehicle.class,
                vehicle,
                "id",
                id
        );

        return vehicle;
    }

    private static void setField(
            Class<?> type,
            Object target,
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                type.getDeclaredField(
                        fieldName
                );

        field.setAccessible(
                true
        );

        field.set(
                target,
                value
        );
    }

    // =========================================================
    // FIXTURE RECORD
    // =========================================================

    private record Fixture(

            VehicleTrackingSessionService service,

            VehicleTrackingSessionRepository trackingSessionRepository,

            VehicleRepository vehicleRepository,

            UserRepository userRepository,

            FleetAccessService fleetAccessService,

            VehicleTrackingRuntimeStore runtimeStore,

            User user,

            Organization organization,

            Vehicle vehicle

    ) {
    }
}