package com.mobilityos.location.controller;

import com.mobilityos.location.dto.GpsPingRequest;
import com.mobilityos.location.service.LocationIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class LocationIngestionController {

    private final LocationIngestionService locationIngestionService;

    public LocationIngestionController(
            LocationIngestionService locationIngestionService
    ) {
        this.locationIngestionService = locationIngestionService;
    }

    @PostMapping("/vehicles/{vehicleId}/location")
    public ResponseEntity<Void> sendPing(
            @PathVariable Long vehicleId,
            @Valid @RequestBody GpsPingRequest request
    ) {
        locationIngestionService.ingestPing(
                currentUserId(),
                vehicleId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}