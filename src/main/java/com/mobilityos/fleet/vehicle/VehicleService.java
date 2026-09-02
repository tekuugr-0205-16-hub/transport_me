package com.mobilityos.fleet.vehicle;

import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.access.FleetAccessService;
import com.mobilityos.fleet.vehicle.dto.CreateVehicleRequest;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final OrganizationRepository organizationRepository;
    private final FleetAccessService fleetAccessService;

    public VehicleService(
            VehicleRepository vehicleRepository,
            OrganizationRepository organizationRepository,
            FleetAccessService fleetAccessService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.organizationRepository = organizationRepository;
        this.fleetAccessService = fleetAccessService;
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
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Operator account not found"
                                )
                        );

        String normalizedPlate =
                normalizePlateNumber(
                        request.plateNumber()
                );

        if (vehicleRepository.existsByPlateNumber(
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
                vehicleRepository.save(vehicle);

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
                .findByOperatorId(organizationId)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private String normalizePlateNumber(
            String plateNumber
    ) {
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Plate number must not be blank"
            );
        }

        return plateNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}