package com.mobilityos.organization.dto;

import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.membership.OrganizationMember;
import com.mobilityos.organization.membership.OrganizationMemberRole;

public record OrganizationResponse(

        Long id,
        String name,
        Organization.OrganizationType type,
        Organization.VerificationStatus verificationStatus,
        OrganizationMemberRole membershipRole

) {

    public static OrganizationResponse from(OrganizationMember membership) {

        Organization organization = membership.getOrganization();

        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getVerificationStatus(),
                membership.getRole()
        );
    }
}