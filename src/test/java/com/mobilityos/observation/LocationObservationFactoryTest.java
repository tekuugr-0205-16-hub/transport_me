package com.mobilityos.location.observation;

import com.mobilityos.location.dto.LocationObservationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocationObservationFactoryTest {

    private static final Long USER_ID =
            501L;

    private static final Long VEHICLE_ID =
            10L;

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

    private static final Instant RECORDED_AT =
            Instant.parse(
                    "2026-09-02T00:30:00Z"
            );

    private static final Instant RECEIVED_AT =
            Instant.parse(
                    "2026-09-02T00:30:02Z"
            );

    private final Validator validator =
            Validation
                    .buildDefaultValidatorFactory()
                    .getValidator();

    private final LocationObservationFactory factory =
            new LocationObservationFactory();

    @Test
    void createsCanonicalMobileObservation() {

        LocationObservationRequest request =
                validRequest();

        LocationObservation observation =
                factory.createMobileObservation(
                        USER_ID,
                        VEHICLE_ID,
                        request,
                        RECEIVED_AT
                );

        assertEquals(
                OBSERVATION_ID,
                observation.observationId()
        );

        assertEquals(
                SESSION_ID,
                observation.trackingSessionId()
        );

        assertEquals(
                847L,
                observation.sequenceNumber()
        );

        assertEquals(
                VEHICLE_ID,
                observation.vehicleId()
        );

        assertEquals(
                USER_ID,
                observation.submittedByUserId()
        );

        assertEquals(
                DEVICE_ID,
                observation.deviceInstallationId()
        );

        assertEquals(
                9.0105,
                observation.latitude()
        );

        assertEquals(
                38.7612,
                observation.longitude()
        );

        assertEquals(
                8.4,
                observation.speedMetersPerSecond()
        );

        assertEquals(
                125.0,
                observation.headingDegrees()
        );

        assertEquals(
                7.5,
                observation.accuracyMeters()
        );

        assertEquals(
                RECORDED_AT,
                observation.recordedAt()
        );

        assertEquals(
                RECEIVED_AT,
                observation.receivedAt()
        );

        assertEquals(
                LocationSource.MOBILE,
                observation.source()
        );
    }

    @Test
    void validPhoneRequestPassesValidation() {

        Set<ConstraintViolation<LocationObservationRequest>>
                violations =
                validator.validate(
                        validRequest()
                );

        assertTrue(
                violations.isEmpty()
        );
    }

    @Test
    void missingRequiredGpsFieldsAreRejected() {

        LocationObservationRequest request =
                new LocationObservationRequest(
                        OBSERVATION_ID,
                        SESSION_ID,
                        1L,
                        DEVICE_ID,
                        null,
                        null,
                        null,
                        null,
                        null,
                        RECORDED_AT
                );

        Set<ConstraintViolation<LocationObservationRequest>>
                violations =
                validator.validate(
                        request
                );

        assertTrue(
                hasViolation(
                        violations,
                        "latitude"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "longitude"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "accuracyMeters"
                )
        );
    }

    @Test
    void invalidSequenceAndGpsRangesAreRejected() {

        LocationObservationRequest request =
                new LocationObservationRequest(
                        OBSERVATION_ID,
                        SESSION_ID,
                        0L,
                        DEVICE_ID,
                        91.0,
                        -181.0,
                        -1.0,
                        360.0,
                        -5.0,
                        RECORDED_AT
                );

        Set<ConstraintViolation<LocationObservationRequest>>
                violations =
                validator.validate(
                        request
                );

        assertTrue(
                hasViolation(
                        violations,
                        "sequenceNumber"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "latitude"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "longitude"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "speedMetersPerSecond"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "headingDegrees"
                )
        );

        assertTrue(
                hasViolation(
                        violations,
                        "accuracyMeters"
                )
        );
    }

    private LocationObservationRequest validRequest() {

        return new LocationObservationRequest(
                OBSERVATION_ID,
                SESSION_ID,
                847L,
                DEVICE_ID,
                9.0105,
                38.7612,
                8.4,
                125.0,
                7.5,
                RECORDED_AT
        );
    }

    private boolean hasViolation(
            Set<ConstraintViolation<LocationObservationRequest>>
                    violations,
            String field
    ) {
        return violations
                .stream()
                .anyMatch(violation ->
                        violation
                                .getPropertyPath()
                                .toString()
                                .equals(field)
                );
    }
}