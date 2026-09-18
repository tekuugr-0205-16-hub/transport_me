package com.mobilityos.fleet.vehicle;

import com.mobilityos.fleet.vehicle.dto.CreateVehicleRequest;
import com.mobilityos.fleet.vehicle.dto.UpdateVehicleStatusRequest;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations/{organizationId}/vehicles")
public class OrganizationVehicleController {

    private final VehicleService vehicleService;

    public OrganizationVehicleController(
            VehicleService vehicleService
    ) {
        this.vehicleService = vehicleService;
    }

    // =========================================================
    // CREATE VEHICLE
    // =========================================================

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        VehicleResponse response =
                vehicleService.createVehicle(
                        currentUserId(),
                        organizationId,
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        response
                );
    }

    // =========================================================
    // LIST ORGANIZATION FLEET
    // =========================================================

    @GetMapping
    public ResponseEntity<List<VehicleResponse>>
    getOrganizationVehicles(
            @PathVariable Long organizationId
    ) {
        List<VehicleResponse> vehicles =
                vehicleService
                        .getVehiclesForOrganization(
                                currentUserId(),
                                organizationId
                        );

        return ResponseEntity.ok(
                vehicles
        );
    }

    // =========================================================
    // UPDATE VEHICLE STATUS
    // =========================================================

    @PatchMapping("/{vehicleId}/status")
    public ResponseEntity<VehicleResponse> updateVehicleStatus(
            @PathVariable Long organizationId,
            @PathVariable Long vehicleId,
            @Valid @RequestBody UpdateVehicleStatusRequest request
    ) {
        VehicleResponse response =
                vehicleService
                        .updateVehicleStatus(
                                currentUserId(),
                                organizationId,
                                vehicleId,
                                request
                        );

        return ResponseEntity.ok(
                response
        );
    }

    // =========================================================
    // CURRENT AUTHENTICATED USER
    // =========================================================

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}