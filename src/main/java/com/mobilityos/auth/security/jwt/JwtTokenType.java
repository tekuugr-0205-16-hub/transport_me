package com.mobilityos.auth.security.jwt;

/**
 * Distinguishes JWTs by purpose. Access tokens authenticate API requests;
 * refresh tokens are accepted only by the token refresh flow.
 */
public enum JwtTokenType {
    ACCESS,
    REFRESH
}
