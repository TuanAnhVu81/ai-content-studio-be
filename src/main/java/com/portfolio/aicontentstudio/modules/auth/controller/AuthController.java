package com.portfolio.aicontentstudio.modules.auth.controller;

import com.portfolio.aicontentstudio.config.properties.RefreshTokenCookieProperties;
import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.CsrfTokenResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.auth.service.AuthService;
import com.portfolio.aicontentstudio.modules.auth.service.RefreshTokenCookieService;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final RefreshTokenCookieProperties refreshTokenCookieProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpServletRequest) {
        AuthSessionResult authSessionResult = authService.login(request, extractClientMetadata(httpServletRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.buildRefreshTokenCookie(authSessionResult.refreshToken()))
                .body(ApiResponse.success("Login successful", toAuthResponse(authSessionResult)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest httpServletRequest) {
        String refreshToken = extractRefreshToken(httpServletRequest);
        AuthSessionResult authSessionResult = authService.refreshToken(refreshToken, extractClientMetadata(httpServletRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.buildRefreshTokenCookie(authSessionResult.refreshToken()))
                .body(ApiResponse.success("Token refreshed successfully", toAuthResponse(authSessionResult)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpServletRequest) {
        authService.logout(extractRefreshToken(httpServletRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearRefreshTokenCookie())
                .body(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> getCsrfToken(CsrfToken csrfToken) {
        CsrfTokenResponse response = new CsrfTokenResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        );
        return ResponseEntity.ok(ApiResponse.success("CSRF token retrieved successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        UserResponse response = authService.getMe();
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearRefreshTokenCookie())
                .body(ApiResponse.success("Password changed successfully", null));
    }

    private AuthResponse toAuthResponse(AuthSessionResult authSessionResult) {
        return new AuthResponse(authSessionResult.accessToken(), authSessionResult.tokenType(), authSessionResult.user());
    }

    private ClientMetadata extractClientMetadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();

        return new ClientMetadata(ipAddress, request.getHeader("User-Agent"));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        Optional<Cookie> refreshCookie = Arrays.stream(cookies)
                .filter(cookie -> refreshTokenCookieProperties.getName().equals(cookie.getName()))
                .findFirst();

        return refreshCookie.map(Cookie::getValue).orElse(null);
    }
}
