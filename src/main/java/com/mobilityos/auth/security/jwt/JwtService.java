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

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(properties.getSecret())
        );
    }

    public String generateAccessToken(JwtClaims claims) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(String.valueOf(claims.userId()))
                .claim("phoneNumber", claims.phoneNumber())
                .claim(TOKEN_TYPE_CLAIM, JwtTokenType.ACCESS.name())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, JwtTokenType.REFRESH.name())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public JwtClaims parseAccessToken(String token) {
        Claims claims = parseTokenOfType(token, JwtTokenType.ACCESS);

        return new JwtClaims(
                Long.valueOf(claims.getSubject()),
                claims.get("phoneNumber", String.class)
        );
    }

    public Long parseUserIdFromRefreshToken(String token) {
        Claims claims = parseTokenOfType(token, JwtTokenType.REFRESH);
        return Long.valueOf(claims.getSubject());
    }

    public boolean isAccessTokenValid(String token) {
        return isTokenValidForType(token, JwtTokenType.ACCESS);
    }

    public boolean isRefreshTokenValid(String token) {
        return isTokenValidForType(token, JwtTokenType.REFRESH);
    }

    private boolean isTokenValidForType(String token, JwtTokenType expectedType) {
        try {
            parseTokenOfType(token, expectedType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseTokenOfType(String token, JwtTokenType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String actualType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.name().equals(actualType)) {
            throw new IllegalArgumentException("Invalid JWT token type");
        }

        return claims;
    }
}
