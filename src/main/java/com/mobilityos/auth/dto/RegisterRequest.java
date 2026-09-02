package com.mobilityos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String phoneNumber,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password
) {}
