package com.mobilityos.organization.dto;

import com.mobilityos.organization.entity.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        Organization.OrganizationType type

) {
}