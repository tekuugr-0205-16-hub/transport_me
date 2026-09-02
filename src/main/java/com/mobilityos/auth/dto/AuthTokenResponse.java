package com.mobilityos.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer");
    }
}
