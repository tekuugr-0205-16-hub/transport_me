package com.mobilityos.fleet.membership.dto;

import com.mobilityos.fleet.membership.VehicleInvitation;
import com.mobilityos.fleet.vehicle.Vehicle;

import java.time.Instant;

public record InvitationResponse(
        Long id,
        Long vehicleId,
        Long organizationId,
        String plateNumber,
        Vehicle.VehicleType vehicleType,
        String invitedPhoneNumber,
        VehicleInvitation.InvitationStatus status,
        Instant createdAt,
        Instant respondedAt
) {

    public static InvitationResponse from(
            VehicleInvitation invitation
    ) {
        Vehicle vehicle = invitation.getVehicle();

        return new InvitationResponse(
                invitation.getId(),
                vehicle.getId(),
                vehicle.getOperator().getId(),
                vehicle.getPlateNumber(),
                vehicle.getVehicleType(),
                invitation.getInvitedPhoneNumber(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getRespondedAt()
        );
    }
}