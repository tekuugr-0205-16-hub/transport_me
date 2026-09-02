package com.mobilityos.location.tracking;

import com.mobilityos.location.tracking.dto.TrackingSessionResponse;
import com.mobilityos.location.tracking.dto.TrackingSessionStartRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/vehicles/{vehicleId}/tracking-sessions")
public class VehicleTrackingSessionController {

    private final VehicleTrackingSessionService trackingSessionService;

    public VehicleTrackingSessionController(
            VehicleTrackingSessionService trackingSessionService
    ) {
        this.trackingSessionService =
                trackingSessionService;
    }

    @PostMapping
    public ResponseEntity<TrackingSessionResponse> startSession(
            @PathVariable Long vehicleId,
            @Valid @RequestBody TrackingSessionStartRequest request
    ) {
        TrackingSessionResponse response =
                trackingSessionService.startSession(
                        currentUserId(),
                        vehicleId,
                        request.deviceInstallationId()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<TrackingSessionResponse> getActiveSession(
            @PathVariable Long vehicleId
    ) {
        TrackingSessionResponse response =
                trackingSessionService.getActiveSession(
                        currentUserId(),
                        vehicleId
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<TrackingSessionResponse> endSession(
            @PathVariable Long vehicleId,
            @PathVariable UUID sessionId
    ) {
        TrackingSessionResponse response =
                trackingSessionService.endSession(
                        currentUserId(),
                        vehicleId,
                        sessionId
                );

        return ResponseEntity.ok(response);
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}