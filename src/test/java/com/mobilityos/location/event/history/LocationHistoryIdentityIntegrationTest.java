package com.mobilityos.location.event.history;

import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.location.entity.VehicleLocationHistory;
import com.mobilityos.location.repository.VehicleLocationHistoryRepository;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LocationHistoryIdentityIntegrationTest {

    private static final String TEST_PHONE =
            "0911999920";

    private static final String TEST_PLATE =
            "AA-9920";

    @Autowired
    private VehicleLocationHistoryRepository historyRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {

        user =
                userRepository.saveAndFlush(
                        new User(
                                TEST_PHONE,
                                "hash"
                        )
                );

        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "History Identity Integration Test",
                                Organization.OrganizationType.PRIVATE_OWNER
                        )
                );

        vehicle =
                vehicleRepository.saveAndFlush(
                        new Vehicle(
                                organization,
                                TEST_PLATE,
                                Vehicle.VehicleType.MINIBUS,
                                12
                        )
                );
    }

    @Test
    void duplicateObservationIdIsRejectedByDatabase() {

        UUID observationId =
                UUID.randomUUID();

        VehicleLocationHistory first =
                canonicalHistory(
                        observationId,
                        UUID.randomUUID(),
                        1L,
                        Instant.parse(
                                "2026-09-18T12:00:00Z"
                        )
                );

        historyRepository.saveAndFlush(
                first
        );

        /*
         * Same observation ID but otherwise a different
         * canonical identity.
         */
        VehicleLocationHistory duplicate =
                canonicalHistory(
                        observationId,
                        UUID.randomUUID(),
                        2L,
                        Instant.parse(
                                "2026-09-18T12:00:10Z"
                        )
                );

        assertThatThrownBy(
                () ->
                        historyRepository.saveAndFlush(
                                duplicate
                        )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    @Test
    void duplicateSessionSequenceIsRejectedByDatabase() {

        UUID trackingSessionId =
                UUID.randomUUID();

        VehicleLocationHistory first =
                canonicalHistory(
                        UUID.randomUUID(),
                        trackingSessionId,
                        10L,
                        Instant.parse(
                                "2026-09-18T12:00:00Z"
                        )
                );

        historyRepository.saveAndFlush(
                first
        );

        /*
         * Different observation UUID but the same
         * tracking-session sequence.
         */
        VehicleLocationHistory duplicate =
                canonicalHistory(
                        UUID.randomUUID(),
                        trackingSessionId,
                        10L,
                        Instant.parse(
                                "2026-09-18T12:00:10Z"
                        )
                );

        assertThatThrownBy(
                () ->
                        historyRepository.saveAndFlush(
                                duplicate
                        )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    @Test
    void legacyRowsMayStillHaveNullCanonicalIdentity() {

        VehicleLocationHistory legacy =
                new VehicleLocationHistory(
                        vehicle,
                        user,
                        9.0300,
                        38.7400,
                        5.0,
                        90.0,
                        8.0,
                        Instant.parse(
                                "2026-09-18T12:00:00Z"
                        ),
                        Instant.parse(
                                "2026-09-18T12:00:01Z"
                        )
                );

        VehicleLocationHistory persisted =
                historyRepository.saveAndFlush(
                        legacy
                );

        assertThat(
                persisted.getObservationId()
        ).isNull();

        assertThat(
                persisted.getTrackingSessionId()
        ).isNull();

        assertThat(
                persisted.getSequenceNumber()
        ).isNull();
    }

    private VehicleLocationHistory canonicalHistory(
            UUID observationId,
            UUID trackingSessionId,
            long sequenceNumber,
            Instant recordedAt
    ) {
        return new VehicleLocationHistory(
                vehicle,
                user,
                observationId,
                trackingSessionId,
                sequenceNumber,
                9.0300,
                38.7400,
                5.0,
                90.0,
                8.0,
                recordedAt,
                recordedAt.plusSeconds(1)
        );
    }
}