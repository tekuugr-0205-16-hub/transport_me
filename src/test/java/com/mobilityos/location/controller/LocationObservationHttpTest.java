package com.mobilityos.location.controller;

import com.mobilityos.common.exception.GlobalExceptionHandler;
import com.mobilityos.location.service.LocationObservationIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class LocationObservationHttpTest {

    private static final Long VEHICLE_ID =
            10L;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private LocationObservationIngestionService ingestionService;

    private MockMvc securityMockMvc;

    private MockMvc validationMockMvc;

    @BeforeEach
    void setUp() {

        clearInvocations(
                ingestionService
        );

        securityMockMvc =
                MockMvcBuilders
                        .webAppContextSetup(
                                applicationContext
                        )
                        .addFilters(
                                springSecurityFilterChain
                        )
                        .build();

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        validationMockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                new LocationObservationController(
                                        ingestionService
                                )
                        )
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .setValidator(
                                validator
                        )
                        .build();
    }

    @Test
    void unauthenticatedObservationIsRejectedBeforeService()
            throws Exception {

        securityMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validJson()
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    @Test
    void missingIdentityFieldsReturnBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "sequenceNumber": 1,
                                          "latitude": 9.0105,
                                          "longitude": 38.7612,
                                          "accuracyMeters": 7.5,
                                          "recordedAt": "2026-09-02T00:30:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.observationId"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.trackingSessionId"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.deviceInstallationId"
                        ).exists()
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    @Test
    void zeroSequenceReturnsBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "observationId":
                                            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                          "trackingSessionId":
                                            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                          "sequenceNumber": 0,
                                          "deviceInstallationId":
                                            "11111111-1111-4111-8111-111111111111",
                                          "latitude": 9.0105,
                                          "longitude": 38.7612,
                                          "accuracyMeters": 7.5,
                                          "recordedAt":
                                            "2026-09-02T00:30:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.sequenceNumber"
                        ).exists()
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    @Test
    void missingRequiredGpsFieldsReturnBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "observationId":
                                            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                          "trackingSessionId":
                                            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                          "sequenceNumber": 1,
                                          "deviceInstallationId":
                                            "11111111-1111-4111-8111-111111111111"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.latitude"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.longitude"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.accuracyMeters"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.recordedAt"
                        ).exists()
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    @Test
    void malformedUuidReturnsBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "observationId": "not-a-uuid",
                                          "trackingSessionId":
                                            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                          "sequenceNumber": 1,
                                          "deviceInstallationId":
                                            "11111111-1111-4111-8111-111111111111",
                                          "latitude": 9.0105,
                                          "longitude": 38.7612,
                                          "accuracyMeters": 7.5,
                                          "recordedAt":
                                            "2026-09-02T00:30:00Z"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.request"
                        ).value(
                                "Malformed or unreadable request body"
                        )
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    @Test
    void malformedRecordedAtReturnsBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/location-observations",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "observationId":
                                            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                          "trackingSessionId":
                                            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                          "sequenceNumber": 1,
                                          "deviceInstallationId":
                                            "11111111-1111-4111-8111-111111111111",
                                          "latitude": 9.0105,
                                          "longitude": 38.7612,
                                          "accuracyMeters": 7.5,
                                          "recordedAt": "bad-time"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.errors.request"
                        ).value(
                                "Malformed or unreadable request body"
                        )
                );

        verifyNoInteractions(
                ingestionService
        );
    }

    private String validJson() {

        return """
                {
                  "observationId":
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                  "trackingSessionId":
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                  "sequenceNumber": 1,
                  "deviceInstallationId":
                    "11111111-1111-4111-8111-111111111111",
                  "latitude": 9.0105,
                  "longitude": 38.7612,
                  "speedMetersPerSecond": 8.4,
                  "headingDegrees": 125.0,
                  "accuracyMeters": 7.5,
                  "recordedAt":
                    "2026-09-02T00:30:00Z"
                }
                """;
    }
}