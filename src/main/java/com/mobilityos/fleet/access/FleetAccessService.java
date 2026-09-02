package com.mobilityos.fleet.access;

import com.mobilityos.common.exception.ForbiddenException;
import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.fleet.membership.VehicleMemberRepository;
import com.mobilityos.fleet.vehicle.Vehicle;
import com.mobilityos.fleet.vehicle.VehicleRepository;
import com.mobilityos.organization.membership.OrganizationMember;
import com.mobilityos.organization.membership.OrganizationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetAccessService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final VehicleMemberRepository vehicleMemberRepository;
    private final VehicleRepository vehicleRepository;

    public FleetAccessService(
            OrganizationMemberRepository organizationMemberRepository,
            VehicleMemberRepository vehicleMemberRepository,
            VehicleRepository vehicleRepository
    ) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.vehicleMemberRepository = vehicleMemberRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public void requireOrganizationManager(
            Long userId,
            Long organizationId
    ) {
        OrganizationMember membership =
                organizationMemberRepository
                        .findByOrganizationIdAndUserId(
                                organizationId,
                                userId
                        )
                        .orElseThrow(() ->
                                new ForbiddenException(
                                        "You do not have access to this operator account"
                                )
                        );

        if (!membership.canManageFleet()) {
            throw new ForbiddenException(
                    "You are not allowed to manage this operator account"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireOrganizationOwner(
            Long userId,
            Long organizationId
    ) {
        OrganizationMember membership =
                organizationMemberRepository
                        .findByOrganizationIdAndUserId(
                                organizationId,
                                userId
                        )
                        .orElseThrow(() ->
                                new ForbiddenException(
                                        "You do not have access to this operator account"
                                )
                        );

        if (!membership.isOwner()) {
            throw new ForbiddenException(
                    "Only the owner can perform this operation"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireVehicleMember(
            Long userId,
            Long vehicleId
    ) {
        if (!vehicleMemberRepository
                .existsByVehicleIdAndUserId(vehicleId, userId)) {

            throw new ForbiddenException(
                    "You do not have operational access to this vehicle"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireVehicleManager(
            Long userId,
            Long vehicleId
    ) {
        Vehicle vehicle = getVehicle(vehicleId);

        if (vehicle.getOperator() == null) {
            throw new ForbiddenException(
                    "This vehicle is not assigned to an operator account"
            );
        }

        requireOrganizationManager(
                userId,
                vehicle.getOperator().getId()
        );
    }

    @Transactional(readOnly = true)
    public boolean canManageVehicle(
            Long userId,
            Long vehicleId
    ) {
        Vehicle vehicle = getVehicle(vehicleId);

        if (vehicle.getOperator() == null) {
            return false;
        }

        return organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        vehicle.getOperator().getId(),
                        userId
                )
                .map(OrganizationMember::canManageFleet)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canOperateVehicle(
            Long userId,
            Long vehicleId
    ) {
        return vehicleMemberRepository
                .existsByVehicleIdAndUserId(
                        vehicleId,
                        userId
                );
    }

    private Vehicle getVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Vehicle not found"
                        )
                );
    }
}