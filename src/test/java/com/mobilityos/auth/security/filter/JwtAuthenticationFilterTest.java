package com.mobilityos.auth.security.filter;

import com.mobilityos.auth.security.jwt.JwtClaims;
import com.mobilityos.auth.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessTokenAuthenticatesUsingSingleParse() throws Exception {
        when(request.getHeader("Authorization"))
                .thenReturn(
                        "Bearer access-token"
                );

        when(jwtService.tryParseAccessToken(
                "access-token"
        )).thenReturn(
                Optional.of(
                        new JwtClaims(
                                42L,
                                "0911000000"
                        )
                )
        );

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(
                        jwtService
                );

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        var authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(
                authentication
        );

        assertEquals(
                Long.valueOf(42L),
                authentication.getPrincipal()
        );

        verify(jwtService, times(1))
                .tryParseAccessToken(
                        "access-token"
                );

        verify(jwtService, never())
                .isAccessTokenValid(
                        anyString()
                );

        verify(jwtService, never())
                .parseAccessToken(
                        anyString()
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void invalidAccessTokenLeavesRequestUnauthenticated() throws Exception {
        when(request.getHeader("Authorization"))
                .thenReturn(
                        "Bearer invalid-token"
                );

        when(jwtService.tryParseAccessToken(
                "invalid-token"
        )).thenReturn(
                Optional.empty()
        );

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(
                        jwtService
                );

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void requestWithoutBearerTokenSkipsJwtParsing() throws Exception {
        when(request.getHeader("Authorization"))
                .thenReturn(
                        null
                );

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(
                        jwtService
                );

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verifyNoInteractions(
                jwtService
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }
}