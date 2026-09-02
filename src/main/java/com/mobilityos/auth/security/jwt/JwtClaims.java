package com.mobilityos.auth.security.jwt;

/** The identity data embedded inside an access token. */
public record JwtClaims(
        Long userId,
        String phoneNumber
) {}
