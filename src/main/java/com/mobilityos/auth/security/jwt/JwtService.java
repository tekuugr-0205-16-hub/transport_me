package com.mobilityos.auth.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Creates and validates JWT access/refresh tokens.
 *
 * Access and refresh tokens are deliberately type-bound. A refresh token
 * cannot authenticate a normal API request, and an access token cannot be
 * exchanged at /auth/refresh.
 *
 * Vehicle authorization is resolved from VehicleMember records, not global
 * OWNER/DRIVER roles.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM =
            "tokenType";

    private static final String PHONE_NUMBER_CLAIM =
            "phoneNumber";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(
            JwtProperties properties
    ) {
        this.properties = properties;

        this.signingKey =
                Keys.hmacShaKeyFor(
                        Base64.getDecoder().decode(
                                properties.getSecret()
                        )
                );
    }

    public String generateAccessToken(
            JwtClaims claims
    ) {
        Instant now =
                Instant.now();

        Instant expiry =
                now.plus(
                        properties
                                .getAccessTokenExpirationMinutes(),
                        ChronoUnit.MINUTES
                );

        return Jwts.builder()
                .subject(
                        String.valueOf(
                                claims.userId()
                        )
                )
                .claim(
                        PHONE_NUMBER_CLAIM,
                        claims.phoneNumber()
                )
                .claim(
                        TOKEN_TYPE_CLAIM,
                        JwtTokenType.ACCESS.name()
                )
                .issuedAt(
                        java.util.Date.from(
                                now
                        )
                )
                .expiration(
                        java.util.Date.from(
                                expiry
                        )
                )
                .signWith(
                        signingKey
                )
                .compact();
    }

    public String generateRefreshToken(
            Long userId
    ) {
        Instant now =
                Instant.now();

        Instant expiry =
                now.plus(
                        properties
                                .getRefreshTokenExpirationDays(),
                        ChronoUnit.DAYS
                );

        return Jwts.builder()
                .subject(
                        String.valueOf(
                                userId
                        )
                )
                .claim(
                        TOKEN_TYPE_CLAIM,
                        JwtTokenType.REFRESH.name()
                )
                .issuedAt(
                        java.util.Date.from(
                                now
                        )
                )
                .expiration(
                        java.util.Date.from(
                                expiry
                        )
                )
                .signWith(
                        signingKey
                )
                .compact();
    }

    /*
     * Throwing parsers remain available for trusted internal
     * callers such as the WebSocket authentication interceptor.
     */
    public JwtClaims parseAccessToken(
            String token
    ) {
        Claims claims =
                parseTokenOfType(
                        token,
                        JwtTokenType.ACCESS
                );

        return accessClaimsFrom(
                claims
        );
    }

    public Long parseUserIdFromRefreshToken(
            String token
    ) {
        Claims claims =
                parseTokenOfType(
                        token,
                        JwtTokenType.REFRESH
                );

        return userIdFrom(
                claims
        );
    }

    /*
     * Safe single-pass parsers for request-boundary code.
     *
     * Each method verifies and parses the JWT only once.
     */
    public Optional<JwtClaims> tryParseAccessToken(
            String token
    ) {
        try {
            Claims claims =
                    parseTokenOfType(
                            token,
                            JwtTokenType.ACCESS
                    );

            return Optional.of(
                    accessClaimsFrom(
                            claims
                    )
            );
        } catch (
                JwtException
                | IllegalArgumentException exception
        ) {
            return Optional.empty();
        }
    }

    public Optional<Long> tryParseRefreshTokenUserId(
            String token
    ) {
        try {
            Claims claims =
                    parseTokenOfType(
                            token,
                            JwtTokenType.REFRESH
                    );

            return Optional.of(
                    userIdFrom(
                            claims
                    )
            );
        } catch (
                JwtException
                | IllegalArgumentException exception
        ) {
            return Optional.empty();
        }
    }

    /*
     * Kept for compatibility with existing callers/tests.
     *
     * New request-boundary code should prefer the tryParse...
     * methods when it needs both validation and claims.
     */
    public boolean isAccessTokenValid(
            String token
    ) {
        return tryParseAccessToken(
                token
        ).isPresent();
    }

    public boolean isRefreshTokenValid(
            String token
    ) {
        return tryParseRefreshTokenUserId(
                token
        ).isPresent();
    }

    private JwtClaims accessClaimsFrom(
            Claims claims
    ) {
        return new JwtClaims(
                userIdFrom(
                        claims
                ),
                claims.get(
                        PHONE_NUMBER_CLAIM,
                        String.class
                )
        );
    }

    private Long userIdFrom(
            Claims claims
    ) {
        return Long.valueOf(
                claims.getSubject()
        );
    }

    private Claims parseTokenOfType(
            String token,
            JwtTokenType expectedType
    ) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(
                                signingKey
                        )
                        .build()
                        .parseSignedClaims(
                                token
                        )
                        .getPayload();

        String actualType =
                claims.get(
                        TOKEN_TYPE_CLAIM,
                        String.class
                );

        if (!expectedType
                .name()
                .equals(
                        actualType
                )) {

            throw new IllegalArgumentException(
                    "Invalid JWT token type"
            );
        }

        return claims;
    }
}