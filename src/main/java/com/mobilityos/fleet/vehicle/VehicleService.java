package com.mobilityos.fleet.vehicle;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.dto.CreateVehicleRequest;
import com.mobilityos.fleet.vehicle.dto.UpdateVehicleStatusRequest;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import com.mobilityos.location.tracking.TrackingSessionEndReason;
import com.mobilityos.location.tracking.VehicleTrackingSessionService;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    private final OrganizationRepository organizationRepository;

    private final FleetAccessService fleetAccessService;

    private final VehicleTrackingSessionService
            trackingSessionService;

    public VehicleService(
            VehicleRepository vehicleRepository,
            OrganizationRepository organizationRepository,
            FleetAccessService fleetAccessService,
            VehicleTrackingSessionService trackingSessionService
    ) {
        this.vehicleRepository =
                Objects.requireNonNull(
                        vehicleRepository,
                        "vehicleRepository must not be null"
                );

        this.organizationRepository =
                Objects.requireNonNull(
                        organizationRepository,
                        "organizationRepository must not be null"
                );

        this.fleetAccessService =
                Objects.requireNonNull(
                        fleetAccessService,
                        "fleetAccessService must not be null"
                );

        this.trackingSessionService =
                Objects.requireNonNull(
                        trackingSessionService,
                        "trackingSessionService must not be null"
                );
    }

    // =========================================================
    // CREATE VEHICLE
    // =========================================================

    @Transactional
    public VehicleResponse createVehicle(
            Long currentUserId,
            Long organizationId,
            CreateVehicleRequest request
    ) {
        /*
         * Only Organization OWNER / ADMIN may register
         * vehicles under this operator account.
         */
        fleetAccessService.requireOrganizationManager(
                currentUserId,
                organizationId
        );

        Organization organization =
                organizationRepository
                        .findById(
                                organizationId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Operator account not found"
                                )
                        );

        String normalizedPlate =
                normalizePlateNumber(
                        request.plateNumber()
                );

        if (vehicleRepository
                .existsByPlateNumber(
                        normalizedPlate
                )) {

            throw new ConflictException(
                    "A vehicle with this plate number already exists"
            );
        }

        Vehicle vehicle =
                new Vehicle(
                        organization,
                        normalizedPlate,
                        request.vehicleType(),
                        request.capacity()
                );

        Vehicle savedVehicle =
                vehicleRepository.save(
                        vehicle
                );

        return VehicleResponse.from(
                savedVehicle
        );
    }

    // =========================================================
    // ORGANIZATION FLEET
    // =========================================================

    @Transactional(readOnly = true)
    public List<VehicleResponse> getVehiclesForOrganization(
            Long currentUserId,
            Long organizationId
    ) {
        /*
         * OWNER / ADMIN can see the operator account's
         * complete fleet.
         *
         * VehicleMembers do not automatically get this access.
         */
        fleetAccessService.requireOrganizationManager(
                currentUserId,
                organizationId
        );

        return vehicleRepository
                .findByOperatorId(
                        organizationId
                )
                .stream()
                .map(
                        VehicleResponse::from
                )
                .toList();
    }

    // =========================================================
    // VEHICLE STATUS
    // =========================================================

    @Transactional
    public VehicleResponse updateVehicleStatus(
            Long currentUserId,
            Long organizationId,
            Long vehicleId,
            UpdateVehicleStatusRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        /*
         * Authorization is against the operator account named
         * in the URL.
         */
        fleetAccessService.requireOrganizationManager(
                currentUserId,
                organizationId
        );

        /*
         * The vehicle row itself is the lifecycle lock.
         *
         * startSession() uses this same PESSIMISTIC_WRITE lock.
         */
        Vehicle vehicle =
                vehicleRepository
                        .findByIdForUpdate(
                                vehicleId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Vehicle not found"
                                )
                        );

        /*
         * Never allow a manager of one organization to mutate a
         * vehicle belonging to another organization simply by
         * supplying its id in the path.
         */
        if (!organizationId.equals(
                vehicle
                        .getOperator()
                        .getId()
        )) {

            throw new ResourceNotFoundException(
                    "Vehicle not found"
            );
        }

        Vehicle.VehicleStatus requestedStatus =
                request.status();

        if (vehicle.getStatus()
                != requestedStatus) {

            vehicle.setStatus(
                    requestedStatus
            );
        }

        /*
         * Any non-ACTIVE state revokes tracking authority.
         *
         * This is deliberately also executed when the vehicle
         * was already INACTIVE / UNDER_MAINTENANCE. That makes
         * the status operation self-healing if inconsistent
         * runtime authority somehow exists.
         *
         * Redis invalidation occurs inside this DB transaction.
         * If Redis invalidation fails, this transaction fails
         * instead of reporting successful deactivation while
         * old runtime authority remains alive.
         */
        if (requestedStatus
                != Vehicle.VehicleStatus.ACTIVE) {

            trackingSessionService
                    .terminateActiveSessionForVehicle(
                            vehicleId,
                            TrackingSessionEndReason
                                    .VEHICLE_DEACTIVATED
                    );
        }

        return VehicleResponse.from(
                vehicle
        );
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private String normalizePlateNumber(
            String plateNumber
    ) {
        if (plateNumber == null
                || plateNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Plate number must not be blank"
            );
        }

        return plateNumber
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }
}