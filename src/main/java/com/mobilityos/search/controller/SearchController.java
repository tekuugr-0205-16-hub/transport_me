
package com.mobilityos.search.controller;

import com.mobilityos.search.dto.NearbyVehicleResponse;
import com.mobilityos.search.nearby.NearbyVehicleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private final NearbyVehicleService nearbyVehicleService;

    public SearchController(NearbyVehicleService nearbyVehicleService) {
        this.nearbyVehicleService = nearbyVehicleService;
    }

    @GetMapping("/search/nearby")
    public ResponseEntity<List<NearbyVehicleResponse>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") double radiusMeters) {
        List<NearbyVehicleResponse> results = nearbyVehicleService.findNearby(lat, lng, radiusMeters);
        return ResponseEntity.ok(results);
    }
}