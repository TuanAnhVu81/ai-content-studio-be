package com.portfolio.aicontentstudio.modules.auth.controller;

import com.portfolio.aicontentstudio.config.SecurityConfig;
import com.portfolio.aicontentstudio.config.properties.RefreshTokenCookieProperties;
import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.core.exception.GlobalExceptionHandler;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.auth.service.AuthService;
import com.portfolio.aicontentstudio.modules.auth.service.RefreshTokenCookieService;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.security.JwtAuthEntryPoint;
import com.portfolio.aicontentstudio.security.JwtAuthFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

    @MockitoBean
    private RefreshTokenCookieProperties refreshTokenCookieProperties;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    private JwtAuthEntryPoint jwtAuthEntryPoint;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_HEADER = "refresh_token=rotated-token; Path=/api/v1/auth; HttpOnly";
    private static final String CLEAR_COOKIE_HEADER = "refresh_token=; Max-Age=0; Path=/api/v1/auth; HttpOnly";

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ((FilterChain) invocation.getArgument(2))
                    .doFilter((ServletRequest) invocation.getArgument(0), (ServletResponse) invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    void loginSuccess_WithCsrfToken_SetsRefreshCookieAndReturnsAccessToken() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UserResponse userResponse = createUserResponse();
        AuthSessionResult authSessionResult = new AuthSessionResult("access-token", "refresh-token", userResponse);
        CsrfTestData csrf = fetchCsrfData();

        given(refreshTokenCookieProperties.getName()).willReturn(REFRESH_COOKIE_NAME);
        given(authService.login(eq(request), any())).willReturn(authSessionResult);
        given(refreshTokenCookieService.buildRefreshTokenCookie("refresh-token")).willReturn(REFRESH_COOKIE_HEADER);

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", REFRESH_COOKIE_HEADER))
                .andExpect(jsonPath("$.data.access_token").value("access-token"))
                .andExpect(jsonPath("$.data.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.refresh_token").doesNotExist());

        verify(authService, times(1)).login(eq(request), any());
    }

    @Test
    void loginFail_WrongPassword_ReturnsUnauthorized() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        given(authService.login(any(LoginRequest.class), any()))
                .willThrow(new AppException(ErrorCode.INVALID_CREDENTIALS));

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"wrong-password"}
                                """)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));
    }

    @Test
    void refreshSuccess_WithCookieAndCsrf_ReturnsNewAccessTokenAndRotatesCookie() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        MockCookie refreshCookie = new MockCookie(REFRESH_COOKIE_NAME, "old-refresh-token");
        UserResponse userResponse = createUserResponse();
        AuthSessionResult authSessionResult = new AuthSessionResult("new-access-token", "rotated-token", userResponse);

        given(refreshTokenCookieProperties.getName()).willReturn(REFRESH_COOKIE_NAME);
        given(authService.refreshToken(eq("old-refresh-token"), any())).willReturn(authSessionResult);
        given(refreshTokenCookieService.buildRefreshTokenCookie("rotated-token")).willReturn(REFRESH_COOKIE_HEADER);

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf.cookie(), refreshCookie)
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", REFRESH_COOKIE_HEADER))
                .andExpect(jsonPath("$.data.access_token").value("new-access-token"));
    }

    @Test
    void refreshFail_WhenCookieMissing_ReturnsUnauthorized() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        given(refreshTokenCookieProperties.getName()).willReturn(REFRESH_COOKIE_NAME);
        given(authService.refreshToken(isNull(), any()))
                .willThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value(ErrorCode.INVALID_REFRESH_TOKEN.getCode()));
    }

    @Test
    void refreshFail_WhenCookieTokenInvalid_ReturnsUnauthorized() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        MockCookie refreshCookie = new MockCookie(REFRESH_COOKIE_NAME, "invalid-refresh-token");

        given(refreshTokenCookieProperties.getName()).willReturn(REFRESH_COOKIE_NAME);
        given(authService.refreshToken(eq("invalid-refresh-token"), any()))
                .willThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf.cookie(), refreshCookie)
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value(ErrorCode.INVALID_REFRESH_TOKEN.getCode()));
    }

    @Test
    void logoutSuccess_ClearsCookieAndRevokesCurrentSession() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        MockCookie refreshCookie = new MockCookie(REFRESH_COOKIE_NAME, "existing-refresh-token");

        given(refreshTokenCookieProperties.getName()).willReturn(REFRESH_COOKIE_NAME);
        given(refreshTokenCookieService.clearRefreshTokenCookie()).willReturn(CLEAR_COOKIE_HEADER);

        // When
        // Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(csrf.cookie(), refreshCookie)
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("refresh_token="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/v1/auth"),
                        containsString("HttpOnly")
                )))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService, times(1)).logout("existing-refresh-token");
    }

    @Test
    void changePasswordSuccess_ClearsCookieAndReturnsOk() throws Exception {
        // Given
        CsrfTestData csrf = fetchCsrfData();
        given(refreshTokenCookieService.clearRefreshTokenCookie()).willReturn(CLEAR_COOKIE_HEADER);

        // When
        // Then
        mockMvc.perform(patch("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"old-pass","new_password":"new-pass-123"}
                                """)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.headerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("refresh_token="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/v1/auth"),
                        containsString("HttpOnly")
                )))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(authService, times(1)).changePassword(new ChangePasswordRequest("old-pass", "new-pass-123"));
    }

    private CsrfTestData fetchCsrfData() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.header_name").value("X-XSRF-TOKEN"))
                .andReturn();

        return new CsrfTestData(
                csrfResult.getResponse().getCookie("XSRF-TOKEN"),
                csrfResult.getResponse().getContentAsString()
        );
    }

    private UserResponse createUserResponse() {
        return new UserResponse(
                UUID.randomUUID(),
                "user@example.com",
                "Demo User",
                AccountStatus.ACTIVE,
                Set.of("ROLE_USER")
        );
    }

    private record CsrfTestData(Cookie cookie, String responseBody) {
        private String headerToken() {
            int start = responseBody.indexOf("\"token\":\"") + 9;
            int end = responseBody.indexOf('"', start);
            return responseBody.substring(start, end);
        }
    }
}
