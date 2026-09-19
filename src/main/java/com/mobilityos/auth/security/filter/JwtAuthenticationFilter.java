package com.mobilityos.auth.security.filter;

import com.mobilityos.auth.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the Authorization: Bearer <token> header on every request,
 * validates it, and stores the authenticated user id in the SecurityContext.
 *
 * Vehicle-level authorization is checked against VehicleMember membership.
 */
@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String AUTH_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(
            JwtService jwtService
    ) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header =
                request.getHeader(
                        AUTH_HEADER
                );

        if (header != null
                && header.startsWith(
                BEARER_PREFIX
        )) {

            String token =
                    header.substring(
                            BEARER_PREFIX.length()
                    );

            /*
             * Parse and verify exactly once.
             *
             * Invalid or expired tokens simply leave the
             * SecurityContext unauthenticated. Spring Security
             * will return the normal JSON 401 response if the
             * target endpoint requires authentication.
             */
            jwtService
                    .tryParseAccessToken(
                            token
                    )
                    .ifPresent(
                            claims -> {
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                claims.userId(),
                                                null,
                                                List.of()
                                        );

                                SecurityContextHolder
                                        .getContext()
                                        .setAuthentication(
                                                authentication
                                        );
                            }
                    );
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}