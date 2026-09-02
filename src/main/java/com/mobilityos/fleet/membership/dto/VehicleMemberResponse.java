package com.mobilityos.fleet.membership.dto;

import com.mobilityos.fleet.membership.VehicleMember;

public record VehicleMemberResponse(
        Long membershipId,
        Long userId,
        String phoneNumber
) {

    public static VehicleMemberResponse from(
            VehicleMember membership
    ) {
        return new VehicleMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getPhoneNumber()
        );
    }
}