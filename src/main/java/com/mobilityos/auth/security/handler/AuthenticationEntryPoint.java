package com.mobilityos.auth.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityos.common.exception.ErrorCode;
import com.mobilityos.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs when an unauthenticated request hits a protected endpoint —
 * returns the same ApiErrorResponse shape as GlobalExceptionHandler,
 * instead of Spring Security's default HTML/blank 401 page.
 */
@Component
public class AuthenticationEntryPoint implements org.springframework.security.web.AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}