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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 42L;
    private static final String PHONE = "0911000000";
    private static final String PASSWORD = "strong-password";
    private static final String PASSWORD_HASH = "encoded-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService service() {
        return new AuthService(
                userRepository,
                passwordEncoder,
                jwtService
        );
    }

    @Test
    void registrationCreatesActiveUserAndIssuesTokens() {
        User savedUser = mock(User.class);

        when(passwordEncoder.encode(PASSWORD))
                .thenReturn(PASSWORD_HASH);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenReturn(savedUser);

        when(savedUser.getId())
                .thenReturn(USER_ID);

        when(savedUser.getPhoneNumber())
                .thenReturn(PHONE);

        when(jwtService.generateAccessToken(any(JwtClaims.class)))
                .thenReturn("access-token");

        when(jwtService.generateRefreshToken(USER_ID))
                .thenReturn("refresh-token");

        AuthTokenResponse response = service().register(
                new RegisterRequest(
                        PHONE,
                        PASSWORD
                )
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .saveAndFlush(
                        userCaptor.capture()
                );

        User createdUser =
                userCaptor.getValue();

        assertEquals(
                PHONE,
                createdUser.getPhoneNumber()
        );

        assertEquals(
                PASSWORD_HASH,
                createdUser.getPasswordHash()
        );

        assertEquals(
                User.UserStatus.ACTIVE,
                createdUser.getStatus()
        );

        assertEquals(
                "access-token",
                response.accessToken()
        );

        assertEquals(
                "refresh-token",
                response.refreshToken()
        );

        assertEquals(
                "Bearer",
                response.tokenType()
        );

        ArgumentCaptor<JwtClaims> claimsCaptor =
                ArgumentCaptor.forClass(JwtClaims.class);

        verify(jwtService)
                .generateAccessToken(
                        claimsCaptor.capture()
                );

        assertEquals(
                USER_ID,
                claimsCaptor.getValue().userId()
        );

        assertEquals(
                PHONE,
                claimsCaptor.getValue().phoneNumber()
        );
    }

    @Test
    void duplicateRegistrationIsRejectedBeforeInsert() {
        when(userRepository.existsByPhoneNumber(PHONE))
                .thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service().register(
                        new RegisterRequest(
                                PHONE,
                                PASSWORD
                        )
                )
        );

        verify(userRepository, never())
                .saveAndFlush(
                        any(User.class)
                );

        verifyNoInteractions(
                jwtService
        );
    }

    @Test
    void concurrentDuplicateRegistrationBecomesConflict() {
        when(passwordEncoder.encode(PASSWORD))
                .thenReturn(PASSWORD_HASH);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate phone"
                        )
                );

        assertThrows(
                ConflictException.class,
                () -> service().register(
                        new RegisterRequest(
                                PHONE,
                                PASSWORD
                        )
                )
        );

        verifyNoInteractions(
                jwtService
        );
    }

    @Test
    void validLoginIssuesTokens() {
        User user = mock(User.class);

        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getPhoneNumber())
                .thenReturn(PHONE);

        when(user.getPasswordHash())
                .thenReturn(PASSWORD_HASH);

        when(user.getStatus())
                .thenReturn(User.UserStatus.ACTIVE);

        when(userRepository.findByPhoneNumber(PHONE))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                PASSWORD,
                PASSWORD_HASH
        )).thenReturn(true);

        when(jwtService.generateAccessToken(any(JwtClaims.class)))
                .thenReturn("access-token");

        when(jwtService.generateRefreshToken(USER_ID))
                .thenReturn("refresh-token");

        AuthTokenResponse response =
                service().login(
                        new LoginRequest(
                                PHONE,
                                PASSWORD
                        )
                );

        assertEquals(
                "access-token",
                response.accessToken()
        );

        assertEquals(
                "refresh-token",
                response.refreshToken()
        );
    }





    @Test
    void unknownPhoneStillPerformsPasswordHashComparison() {
        when(userRepository.findByPhoneNumber(PHONE))
                .thenReturn(
                        Optional.empty()
                );

        assertThrows(
                UnauthorizedException.class,
                () -> service().login(
                        new LoginRequest(
                                PHONE,
                                PASSWORD
                        )
                )
        );

        /*
         * Structural timing-equalisation test.
         *
         * We intentionally do not test milliseconds because
         * timing assertions would be unreliable on CI/dev
         * machines.
         */
        verify(passwordEncoder)
                .matches(
                        eq(PASSWORD),
                        anyString()
                );

        verifyNoInteractions(
                jwtService
        );
    }

    @Test
    void wrongPasswordIsRejected() {
        User user = mock(User.class);

        when(userRepository.findByPhoneNumber(PHONE))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getPasswordHash())
                .thenReturn(PASSWORD_HASH);

        assertThrows(
                UnauthorizedException.class,
                () -> service().login(
                        new LoginRequest(
                                PHONE,
                                PASSWORD
                        )
                )
        );

        verify(passwordEncoder)
                .matches(
                        PASSWORD,
                        PASSWORD_HASH
                );

        verifyNoInteractions(
                jwtService
        );
    }

    @Test
    void suspendedUserCannotLogin() {
        assertInactiveLoginRejected(
                User.UserStatus.SUSPENDED
        );
    }

    @Test
    void pendingVerificationUserCannotLogin() {
        assertInactiveLoginRejected(
                User.UserStatus.PENDING_VERIFICATION
        );
    }



    @Test
    void validRefreshIssuesNewTokens() {
        User user = mock(User.class);

        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getPhoneNumber())
                .thenReturn(PHONE);

        when(user.getStatus())
                .thenReturn(User.UserStatus.ACTIVE);

        when(jwtService.tryParseRefreshTokenUserId(
                "refresh-token"
        )).thenReturn(
                Optional.of(USER_ID)
        );

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(jwtService.generateAccessToken(any(JwtClaims.class)))
                .thenReturn("new-access-token");

        when(jwtService.generateRefreshToken(USER_ID))
                .thenReturn("new-refresh-token");

        AuthTokenResponse response =
                service().refresh(
                        "refresh-token"
                );

        assertEquals(
                "new-access-token",
                response.accessToken()
        );

        assertEquals(
                "new-refresh-token",
                response.refreshToken()
        );

        verify(jwtService)
                .tryParseRefreshTokenUserId(
                        "refresh-token"
                );

        verify(jwtService, never())
                .isRefreshTokenValid(
                        anyString()
                );

        verify(jwtService, never())
                .parseUserIdFromRefreshToken(
                        anyString()
                );
    }




    @Test
    void invalidRefreshTokenIsRejectedBeforeDatabaseLookup() {
        when(jwtService.tryParseRefreshTokenUserId(
                "invalid-token"
        )).thenReturn(
                Optional.empty()
        );

        assertThrows(
                UnauthorizedException.class,
                () -> service().refresh(
                        "invalid-token"
                )
        );

        verify(userRepository, never())
                .findById(
                        anyLong()
                );
    }

    @Test
    void deletedUserCannotRefresh() {
        when(jwtService.tryParseRefreshTokenUserId(
                "refresh-token"
        )).thenReturn(
                Optional.of(USER_ID)
        );

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.empty()
                );

        assertThrows(
                UnauthorizedException.class,
                () -> service().refresh(
                        "refresh-token"
                )
        );
    }

    @Test
    void suspendedUserCannotRefresh() {
        assertInactiveRefreshRejected(
                User.UserStatus.SUSPENDED
        );
    }

    @Test
    void pendingVerificationUserCannotRefresh() {
        assertInactiveRefreshRejected(
                User.UserStatus.PENDING_VERIFICATION
        );
    }

    private void assertInactiveLoginRejected(
            User.UserStatus status
    ) {
        User user = mock(User.class);

        when(userRepository.findByPhoneNumber(PHONE))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getPasswordHash())
                .thenReturn(PASSWORD_HASH);

        when(passwordEncoder.matches(
                PASSWORD,
                PASSWORD_HASH
        )).thenReturn(true);

        when(user.getStatus())
                .thenReturn(status);

        assertThrows(
                UnauthorizedException.class,
                () -> service().login(
                        new LoginRequest(
                                PHONE,
                                PASSWORD
                        )
                )
        );

        verifyNoInteractions(
                jwtService
        );
    }

    private void assertInactiveRefreshRejected(
            User.UserStatus status
    ) {
        User user = mock(User.class);

        when(jwtService.tryParseRefreshTokenUserId(
                "refresh-token"
        )).thenReturn(
                Optional.of(USER_ID)
        );

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getStatus())
                .thenReturn(status);

        assertThrows(
                UnauthorizedException.class,
                () -> service().refresh(
                        "refresh-token"
                )
        );

        verify(jwtService, never())
                .generateAccessToken(
                        any(JwtClaims.class)
                );

        verify(jwtService, never())
                .generateRefreshToken(
                        anyLong()
                );
    }

    private User activeUser() {
        User user = mock(User.class);

        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getPhoneNumber())
                .thenReturn(PHONE);

        when(user.getPasswordHash())
                .thenReturn(PASSWORD_HASH);

        when(user.getStatus())
                .thenReturn(
                        User.UserStatus.ACTIVE
                );

        return user;
    }
}