package com.mobilityos.fleet.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(

        @NotBlank
        @Size(max = 20)
        String phoneNumber

) {}