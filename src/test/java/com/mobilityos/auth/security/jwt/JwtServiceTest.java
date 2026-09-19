package com.mobilityos.auth.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties =
                new JwtProperties();

        properties.setSecret(
                "c2VjdXJlLWRldi1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tMjU2Yml0"
        );

        properties.setAccessTokenExpirationMinutes(
                30
        );

        properties.setRefreshTokenExpirationDays(
                30
        );

        jwtService =
                new JwtService(
                        properties
                );
    }

    @Test
    void accessTokenIsAcceptedOnlyAsAccessToken() {
        String token =
                jwtService.generateAccessToken(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                );

        assertTrue(
                jwtService.isAccessTokenValid(
                        token
                )
        );

        assertFalse(
                jwtService.isRefreshTokenValid(
                        token
                )
        );

        JwtClaims claims =
                jwtService.parseAccessToken(
                        token
                );

        assertEquals(
                Long.valueOf(42L),
                claims.userId()
        );

        assertEquals(
                "0911000000",
                claims.phoneNumber()
        );
    }

    @Test
    void refreshTokenIsAcceptedOnlyAsRefreshToken() {
        String token =
                jwtService.generateRefreshToken(
                        42L
                );

        assertTrue(
                jwtService.isRefreshTokenValid(
                        token
                )
        );

        assertFalse(
                jwtService.isAccessTokenValid(
                        token
                )
        );

        assertEquals(
                Long.valueOf(42L),
                jwtService.parseUserIdFromRefreshToken(
                        token
                )
        );
    }

    @Test
    void refreshTokenCannotBeParsedAsAccessToken() {
        String refreshToken =
                jwtService.generateRefreshToken(
                        42L
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.parseAccessToken(
                        refreshToken
                )
        );
    }

    @Test
    void accessTokenCannotBeParsedAsRefreshToken() {
        String accessToken =
                jwtService.generateAccessToken(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.parseUserIdFromRefreshToken(
                        accessToken
                )
        );
    }

    @Test
    void singlePassAccessParserReturnsClaims() {
        String token =
                jwtService.generateAccessToken(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                );

        var result =
                jwtService.tryParseAccessToken(
                        token
                );

        assertTrue(
                result.isPresent()
        );

        assertEquals(
                Long.valueOf(42L),
                result.orElseThrow().userId()
        );

        assertEquals(
                "0911000000",
                result.orElseThrow().phoneNumber()
        );
    }

    @Test
    void singlePassAccessParserRejectsRefreshToken() {
        String refreshToken =
                jwtService.generateRefreshToken(
                        42L
                );

        assertTrue(
                jwtService.tryParseAccessToken(
                        refreshToken
                ).isEmpty()
        );
    }

    @Test
    void singlePassRefreshParserReturnsUserId() {
        String refreshToken =
                jwtService.generateRefreshToken(
                        42L
                );

        var result =
                jwtService.tryParseRefreshTokenUserId(
                        refreshToken
                );

        assertTrue(
                result.isPresent()
        );

        assertEquals(
                Long.valueOf(42L),
                result.orElseThrow()
        );
    }

    @Test
    void singlePassRefreshParserRejectsAccessToken() {
        String accessToken =
                jwtService.generateAccessToken(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                );

        assertTrue(
                jwtService.tryParseRefreshTokenUserId(
                        accessToken
                ).isEmpty()
        );
    }

    @Test
    void malformedTokenIsRejectedSafely() {
        assertTrue(
                jwtService.tryParseAccessToken(
                        "not-a-jwt"
                ).isEmpty()
        );

        assertTrue(
                jwtService.tryParseRefreshTokenUserId(
                        "not-a-jwt"
                ).isEmpty()
        );
    }

    @Test
    void tamperedAccessTokenIsRejected() {
        String token =
                jwtService.generateAccessToken(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                );

        String tampered =
                tamperPayload(
                        token
                );

        assertTrue(
                jwtService.tryParseAccessToken(
                        tampered
                ).isEmpty()
        );
    }

    @Test
    void tamperedRefreshTokenIsRejected() {
        String token =
                jwtService.generateRefreshToken(
                        42L
                );

        String tampered =
                tamperPayload(
                        token
                );

        assertTrue(
                jwtService.tryParseRefreshTokenUserId(
                        tampered
                ).isEmpty()
        );
    }

    private String tamperPayload(
            String token
    ) {
        String[] parts =
                token.split("\\.");

        assertEquals(
                3,
                parts.length
        );

        String payload =
                parts[1];

        char replacement =
                payload.charAt(0) == 'A'
                        ? 'B'
                        : 'A';

        String changedPayload =
                replacement
                        + payload.substring(1);

        return parts[0]
                + "."
                + changedPayload
                + "."
                + parts[2];
    }
}