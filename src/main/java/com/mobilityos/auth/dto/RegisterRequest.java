package com.mobilityos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(
                max = 20,
                message = "Phone number must not exceed 20 characters"
        )
        String phoneNumber,

        @NotBlank
        @Size(
                min = 8,
                max = 128,
                message = "Password must be between 8 and 128 characters"
        )
        String password

) {}