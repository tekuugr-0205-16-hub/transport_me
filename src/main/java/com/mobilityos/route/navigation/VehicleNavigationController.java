package com.mobilityos.route.navigation;

import com.mobilityos.route.navigation.dto.NavigationOption;
import com.mobilityos.route.navigation.dto.NavigationOptionsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vehicles/{vehicleId}/navigation")
public class VehicleNavigationController {

    private final VehicleNavigationService vehicleNavigationService;

    public VehicleNavigationController(
            VehicleNavigationService vehicleNavigationService
    ) {
        this.vehicleNavigationService =
                vehicleNavigationService;
    }

    // =========================================================
    // NAVIGATION OPTIONS
    // =========================================================

    @PostMapping("/options")
    public ResponseEntity<List<NavigationOption>>
    getNavigationOptions(
            @PathVariable Long vehicleId,
            @Valid @RequestBody NavigationOptionsRequest request
    ) {
        List<NavigationOption> options =
                vehicleNavigationService
                        .getNavigationOptions(
                                currentUserId(),
                                vehicleId,
                                request
                        );

        return ResponseEntity.ok(
                options
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