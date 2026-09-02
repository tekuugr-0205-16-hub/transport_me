package com.mobilityos.auth.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("c2VjdXJlLWRldi1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tMjU2Yml0");
        properties.setAccessTokenExpirationMinutes(30);
        properties.setRefreshTokenExpirationDays(30);
        jwtService = new JwtService(properties);
    }

    @Test
    void accessTokenIsAcceptedOnlyAsAccessToken() {
        String token = jwtService.generateAccessToken(new JwtClaims(42L, "0911000000"));

        assertTrue(jwtService.isAccessTokenValid(token));
        assertFalse(jwtService.isRefreshTokenValid(token));

        JwtClaims claims = jwtService.parseAccessToken(token);
        assertEquals(Long.valueOf(42L), claims.userId());
        assertEquals("0911000000", claims.phoneNumber());
    }

    @Test
    void refreshTokenIsAcceptedOnlyAsRefreshToken() {
        String token = jwtService.generateRefreshToken(42L);

        assertTrue(jwtService.isRefreshTokenValid(token));
        assertFalse(jwtService.isAccessTokenValid(token));
        assertEquals(Long.valueOf(42L), jwtService.parseUserIdFromRefreshToken(token));
    }

    @Test
    void refreshTokenCannotBeParsedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(42L);

        assertThrows(IllegalArgumentException.class,
                () -> jwtService.parseAccessToken(refreshToken));
    }

    @Test
    void accessTokenCannotBeParsedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(new JwtClaims(42L, "0911000000"));

        assertThrows(IllegalArgumentException.class,
                () -> jwtService.parseUserIdFromRefreshToken(accessToken));
    }
}
