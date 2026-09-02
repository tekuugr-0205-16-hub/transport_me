package com.mobilityos.auth.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mobilityos.jwt")
public class JwtProperties {

    /** Base64-encoded HMAC signing key. Must be at least 256 bits for HS256. */
    private String secret;

    /** Access token lifetime, in minutes. Short-lived by design. */
    private long accessTokenExpirationMinutes = 15;

    /** Refresh token lifetime, in days. */
    private long refreshTokenExpirationDays = 30;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTokenExpirationMinutes() { return accessTokenExpirationMinutes; }
    public void setAccessTokenExpirationMinutes(long v) { this.accessTokenExpirationMinutes = v; }

    public long getRefreshTokenExpirationDays() { return refreshTokenExpirationDays; }
    public void setRefreshTokenExpirationDays(long v) { this.refreshTokenExpirationDays = v; }
}
