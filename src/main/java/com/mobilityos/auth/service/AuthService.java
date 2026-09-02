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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new ConflictException("A user with this phone number already exists");
        }

        User user = new User(request.phoneNumber(), passwordEncoder.encode(request.password()));
        user.setStatus(User.UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new UnauthorizedException("Invalid phone number or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid phone number or password");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active");
        }

        return issueTokens(user);
    }

    public AuthTokenResponse refresh(String refreshToken) {
        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        Long userId = jwtService.parseUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        return issueTokens(user);
    }

    private AuthTokenResponse issueTokens(User user) {
        JwtClaims claims = new JwtClaims(user.getId(), user.getPhoneNumber());
        String accessToken = jwtService.generateAccessToken(claims);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return AuthTokenResponse.of(accessToken, refreshToken);
    }
}
