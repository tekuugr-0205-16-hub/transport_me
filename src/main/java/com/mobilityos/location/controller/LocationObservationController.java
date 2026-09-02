package com.mobilityos.location.controller;

import com.mobilityos.location.dto.LocationObservationRequest;
import com.mobilityos.location.dto.LocationObservationResponse;
import com.mobilityos.location.observation.LocationObservationIngestionResult;
import com.mobilityos.location.service.LocationObservationIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vehicles/{vehicleId}/location-observations")
public class LocationObservationController {

    private final LocationObservationIngestionService ingestionService;

    public LocationObservationController(
            LocationObservationIngestionService ingestionService
    ) {
        this.ingestionService =
                ingestionService;
    }

    @PostMapping
    public ResponseEntity<LocationObservationResponse> ingest(
            @PathVariable Long vehicleId,
            @Valid @RequestBody LocationObservationRequest request
    ) {
        LocationObservationIngestionResult result =
                ingestionService.ingest(
                        currentUserId(),
                        vehicleId,
                        request
                );

        LocationObservationResponse response =
                new LocationObservationResponse(
                        result
                );

        return switch (result) {

            case APPLIED,
                 DUPLICATE_CURRENT,
                 STALE_SEQUENCE ->
                    ResponseEntity.ok(
                            response
                    );

            case REJECTED_QUALITY ->
                    ResponseEntity
                            .status(422)
                            .body(
                                    response
                            );

            case NO_ACTIVE_SESSION,
                 SESSION_MISMATCH,
                 DEVICE_MISMATCH ->
                    ResponseEntity
                            .status(409)
                            .body(
                                    response
                            );

            case USER_MISMATCH ->
                    ResponseEntity
                            .status(403)
                            .body(
                                    response
                            );
        };
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}