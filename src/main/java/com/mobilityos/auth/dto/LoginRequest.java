package com.mobilityos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Size(
                max = 20,
                message = "Phone number must not exceed 20 characters"
        )
        String phoneNumber,

        @NotBlank
        @Size(
                max = 128,
                message = "Password must not exceed 128 characters"
        )
        String password

) {}