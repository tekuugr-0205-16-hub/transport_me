package com.mobilityos.location.tracking;

import com.mobilityos.common.exception.GlobalExceptionHandler;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VehicleTrackingSessionHttpTest {

    private static final Long VEHICLE_ID =
            10L;

    private static final String DEVICE_ID =
            "11111111-1111-4111-8111-111111111111";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private VehicleTrackingSessionService trackingSessionService;

    private MockMvc securityMockMvc;

    private MockMvc validationMockMvc;

    @BeforeEach
    void setUp() {

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
                                new VehicleTrackingSessionController(
                                        trackingSessionService
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
    void unauthenticatedStartIsRejectedBeforeService()
            throws Exception {

        securityMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/tracking-sessions",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "deviceInstallationId":
                                            "%s"
                                        }
                                        """.formatted(
                                                DEVICE_ID
                                        )
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
                trackingSessionService
        );
    }

    @Test
    void missingDeviceInstallationIdReturnsBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/tracking-sessions",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{}"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.errors.deviceInstallationId"
                        ).exists()
                );

        verifyNoInteractions(
                trackingSessionService
        );
    }

    @Test
    void malformedDeviceInstallationIdReturnsBadRequest()
            throws Exception {

        validationMockMvc.perform(
                        post(
                                "/vehicles/{vehicleId}/tracking-sessions",
                                VEHICLE_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "deviceInstallationId":
                                            "not-a-uuid"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.errors.request"
                        ).value(
                                "Malformed or unreadable request body"
                        )
                );

        verifyNoInteractions(
                trackingSessionService
        );
    }
}