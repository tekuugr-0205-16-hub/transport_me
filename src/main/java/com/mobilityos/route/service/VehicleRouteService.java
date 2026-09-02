package com.mobilityos.route.service;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.route.dto.StartVehicleRouteRequest;
import com.mobilityos.route.dto.VehicleRouteResponse;
import com.mobilityos.route.entity.VehicleRoute;
import com.mobilityos.route.repository.VehicleRouteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class VehicleRouteService {

    private final VehicleRouteRepository vehicleRouteRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final FleetAccessService fleetAccessService;

    public VehicleRouteService(
            VehicleRouteRepository vehicleRouteRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            FleetAccessService fleetAccessService
    ) {
        this.vehicleRouteRepository = vehicleRouteRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.fleetAccessService = fleetAccessService;
    }

    // =========================================================
    // START ROUTE
    // =========================================================

    @Transactional
    public VehicleRouteResponse startRoute(
            Long currentUserId,
            Long vehicleId,
            StartVehicleRouteRequest request
    ) {
        Vehicle vehicle =
                getVehicle(vehicleId);

        /*
         * Route operation belongs to VehicleMembers.
         *
         * Organization OWNER / ADMIN may monitor the route,
         * but cannot start one unless they are also explicitly
         * assigned as a VehicleMember.
         */
        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        if (!vehicle.isActive()) {
            throw new ForbiddenException(
                    "Only ACTIVE vehicles can start a route"
            );
        }

        if (vehicleRouteRepository
                .findByVehicleIdAndStatus(
                        vehicleId,
                        VehicleRoute.RouteStatus.ACTIVE
                )
                .isPresent()) {

            throw new ConflictException(
                    "This vehicle already has an active route"
            );
        }

        User currentUser =
                getUser(currentUserId);

        VehicleRoute route =
                new VehicleRoute(
                        vehicle,
                        currentUser,
                        request.originName(),
                        request.destinationName()
                );

        try {
            VehicleRoute savedRoute =
                    vehicleRouteRepository.saveAndFlush(
                            route
                    );

            return VehicleRouteResponse.from(
                    savedRoute
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * V12's partial unique index is the final protection
             * against two concurrent requests starting routes for
             * the same vehicle.
             */
            throw new ConflictException(
                    "This vehicle already has an active route"
            );
        }
    }

    // =========================================================
    // END CURRENT ROUTE
    // =========================================================

    @Transactional
    public VehicleRouteResponse endCurrentRoute(
            Long currentUserId,
            Long vehicleId
    ) {
        getVehicle(vehicleId);

        fleetAccessService.requireVehicleMember(
                currentUserId,
                vehicleId
        );

        VehicleRoute route =
                vehicleRouteRepository
                        .findByVehicleIdAndStatus(
                                vehicleId,
                                VehicleRoute.RouteStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "This vehicle has no active route"
                                )
                        );

        route.end(
                Instant.now()
        );

        return VehicleRouteResponse.from(
                route
        );
    }

    // =========================================================
    // CURRENT ROUTE
    // =========================================================

    @Transactional(readOnly = true)
    public VehicleRouteResponse getCurrentRoute(
            Long vehicleId
    ) {
        getVehicle(vehicleId);

        VehicleRoute route =
                vehicleRouteRepository
                        .findByVehicleIdAndStatus(
                                vehicleId,
                                VehicleRoute.RouteStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "This vehicle has no active route"
                                )
                        );

        return VehicleRouteResponse.from(
                route
        );
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private Vehicle getVehicle(
            Long vehicleId
    ) {
        return vehicleRepository
                .findById(vehicleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Vehicle not found"
                        )
                );
    }

    private User getUser(
            Long userId
    ) {
        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );
    }
}