package com.portfolio.aicontentstudio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a structured JSON 401 error when an unauthenticated user tries to access a protected endpoint.
 * Prevents Spring Security from returning the default Tomcat error page.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> errorResponse = ApiResponse.error("Authentication required. Please log in.");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
