package com.mobilityos.auth.service;

import com.mobilityos.auth.dto.AuthTokenResponse;
import com.mobilityos.auth.dto.LoginRequest;
import com.mobilityos.auth.dto.RegisterRequest;
import com.mobilityos.auth.security.jwt.JwtClaims;
import com.mobilityos.auth.security.jwt.JwtService;
import com.mobilityos.common.exception.ConflictException;
import com.mobilityos.common.exception.UnauthorizedException;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /*
     * A syntactically valid BCrypt hash used only to make the
     * "unknown phone number" login path perform BCrypt work too.
     *
     * The comparison result is ignored whenever the user does
     * not exist.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid phone number or password";

    private static final String INVALID_REFRESH_TOKEN_MESSAGE =
            "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokenResponse register(
            RegisterRequest request
    ) {
        if (userRepository.existsByPhoneNumber(
                request.phoneNumber()
        )) {
            throw duplicatePhoneNumber();
        }

        User user = new User(
                request.phoneNumber(),
                passwordEncoder.encode(
                        request.password()
                )
        );

        /*
         * Preserve the existing MVP behaviour:
         * registration activates the account immediately.
         *
         * Phone verification can later introduce an explicit
         * PENDING_VERIFICATION -> ACTIVE lifecycle.
         */
        user.setStatus(
                User.UserStatus.ACTIVE
        );

        final User saved;

        try {
            /*
             * Flush here so the database UNIQUE(phone_number)
             * constraint is checked inside this method.
             *
             * This closes the race where two registrations both
             * pass existsByPhoneNumber() concurrently.
             */
            saved = userRepository.saveAndFlush(
                    user
            );
        } catch (DataIntegrityViolationException exception) {
            throw duplicatePhoneNumber();
        }

        return issueTokens(
                saved
        );
    }

    public AuthTokenResponse login(
            LoginRequest request
    ) {
        User user = userRepository
                .findByPhoneNumber(
                        request.phoneNumber()
                )
                .orElse(null);

        /*
         * Always perform one password-hash comparison.
         *
         * Without this, an unknown phone number returns before
         * BCrypt runs while a known phone number performs the
         * expensive BCrypt comparison. That difference can leak
         * account existence through response timing.
         */
        String passwordHash =
                user != null
                        ? user.getPasswordHash()
                        : DUMMY_PASSWORD_HASH;

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.password(),
                        passwordHash
                );

        if (user == null
                || !passwordMatches) {

            throw new UnauthorizedException(
                    INVALID_CREDENTIALS_MESSAGE
            );
        }

        requireActive(
                user
        );

        return issueTokens(
                user
        );
    }

    public AuthTokenResponse refresh(
            String refreshToken
    ) {
        /*
         * Parse and cryptographically verify the refresh token
         * exactly once.
         */
        Long userId = jwtService
                .tryParseRefreshTokenUserId(
                        refreshToken
                )
                .orElseThrow(
                        () -> new UnauthorizedException(
                                INVALID_REFRESH_TOKEN_MESSAGE
                        )
                );

        User user = userRepository
                .findById(
                        userId
                )
                .orElseThrow(
                        () -> new UnauthorizedException(
                                INVALID_REFRESH_TOKEN_MESSAGE
                        )
                );

        /*
         * A suspended or still-unverified account must not be
         * able to create new access tokens from an old refresh
         * token.
         */
        requireActive(
                user
        );

        return issueTokens(
                user
        );
    }

    private void requireActive(
            User user
    ) {
        if (user.getStatus()
                != User.UserStatus.ACTIVE) {

            throw new UnauthorizedException(
                    "Account is not active"
            );
        }
    }

    private AuthTokenResponse issueTokens(
            User user
    ) {
        JwtClaims claims = new JwtClaims(
                user.getId(),
                user.getPhoneNumber()
        );

        String accessToken =
                jwtService.generateAccessToken(
                        claims
                );

        String refreshToken =
                jwtService.generateRefreshToken(
                        user.getId()
                );

        return AuthTokenResponse.of(
                accessToken,
                refreshToken
        );
    }

    private ConflictException duplicatePhoneNumber() {
        return new ConflictException(
                "A user with this phone number already exists"
        );
    }
}