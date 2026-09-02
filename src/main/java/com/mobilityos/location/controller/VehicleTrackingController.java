package com.mobilityos.location.controller;

import com.mobilityos.location.dto.LiveLocationResponse;
import com.mobilityos.location.service.VehicleTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class VehicleTrackingController {

    private final VehicleTrackingService vehicleTrackingService;

    public VehicleTrackingController(
            VehicleTrackingService vehicleTrackingService
    ) {
        this.vehicleTrackingService = vehicleTrackingService;
    }

    @GetMapping("/vehicles/{vehicleId}/location")
    public ResponseEntity<LiveLocationResponse> getLiveLocation(
            @PathVariable Long vehicleId
    ) {
        LiveLocationResponse response =
                vehicleTrackingService.getLiveLocation(
                        vehicleId
                );

        return ResponseEntity.ok(response);
    }
}