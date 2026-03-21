package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshTokenRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AuthServiceImpl using JUnit 5, Mockito, and AssertJ.
 * Focuses on register, login, refreshToken, and logout logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: register(RegisterRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void register_ValidRequest_SavesUserSuccessfully() {
        // Given
        RegisterRequest request = createMockRegisterRequest();
        Role defaultRole = createMockRole();
        String encodedPassword = "encoded_password_123";

        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(defaultRole));
        given(passwordEncoder.encode(request.password())).willReturn(encodedPassword);

        // When
        authService.register(request);

        // Then
        verify(userRepository, times(1)).existsByEmail(request.email());
        verify(roleRepository, times(1)).findByName("ROLE_USER");
        verify(passwordEncoder, times(1)).encode(request.password());
        
        verify(userRepository, times(1)).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        
        assertThat(capturedUser).isNotNull();
        assertThat(capturedUser.getEmail()).isEqualTo(request.email());
        assertThat(capturedUser.getFullName()).isEqualTo(request.fullName());
        assertThat(capturedUser.getPasswordHash()).isEqualTo(encodedPassword);
        assertThat(capturedUser.getRoles()).hasSize(1).contains(defaultRole);
    }

    @Test
    void register_EmailAlreadyExists_ThrowsAppException() {
        // Given
        RegisterRequest request = createMockRegisterRequest();
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(roleRepository, never()).findByName(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_RoleNotFound_ThrowsAppException() {
        // Given
        RegisterRequest request = createMockRegisterRequest();
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_FOUND);

        verify(roleRepository, times(1)).findByName("ROLE_USER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_NullRequest_ThrowsNullPointerException() {
        // Given
        RegisterRequest request = null;

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(NullPointerException.class);

        verify(userRepository, never()).existsByEmail(anyString());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: login(LoginRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void login_ValidCredentials_ReturnsAuthResponse() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UserDetails userDetails = mock(UserDetails.class);
        User user = createMockUser();
        String mockAccessToken = "mock_access_token";

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        given(userDetailsService.loadUserByUsername(request.email())).willReturn(userDetails);
        given(jwtProvider.generateAccessToken(userDetails)).willReturn(mockAccessToken);
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // When
        AuthResponse response = authService.login(request);

        // Then
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userDetailsService, times(1)).loadUserByUsername(request.email());
        verify(jwtProvider, times(1)).generateAccessToken(userDetails);
        verify(userRepository, times(1)).findByEmail(request.email());
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set(startsWith("rt:"), eq(user.getId().toString()), eq(0L), eq(TimeUnit.MILLISECONDS));

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(mockAccessToken);
        assertThat(response.refreshToken()).isNotNull();
    }

    @Test
    void login_InvalidCredentials_ThrowsAppException() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrong_password");

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(jwtProvider, never()).generateAccessToken(any(UserDetails.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void login_UserNotFoundAfterAuth_ThrowsAppException() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UserDetails userDetails = mock(UserDetails.class);

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        given(userDetailsService.loadUserByUsername(request.email())).willReturn(userDetails);
        given(jwtProvider.generateAccessToken(userDetails)).willReturn("mock_access_token");
        given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findByEmail(request.email());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void login_NullRequest_ThrowsNullPointerException() {
        // Given
        LoginRequest request = null;

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(NullPointerException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: refreshToken(RefreshTokenRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void refreshToken_ValidToken_ReturnsNewAuthResponse() {
        // Given
        String incomingToken = "valid_refresh_token";
        RefreshTokenRequest request = new RefreshTokenRequest(incomingToken);
        User user = createMockUser();
        UserDetails userDetails = mock(UserDetails.class);
        String newAccessToken = "new_mock_access_token";

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("rt:" + incomingToken)).willReturn(user.getId().toString());
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userDetailsService.loadUserByUsername(user.getEmail())).willReturn(userDetails);
        given(jwtProvider.generateAccessToken(userDetails)).willReturn(newAccessToken);

        // When
        AuthResponse response = authService.refreshToken(request);

        // Then
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get("rt:" + incomingToken);
        verify(userRepository, times(1)).findById(user.getId());
        verify(userDetailsService, times(1)).loadUserByUsername(user.getEmail());
        verify(jwtProvider, times(1)).generateAccessToken(userDetails);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(newAccessToken);
        assertThat(response.refreshToken()).isEqualTo(incomingToken);
    }

    @Test
    void refreshToken_InvalidTokenNotFoundInRedis_ThrowsAppException() {
        // Given
        String incomingToken = "invalid_refresh_token";
        RefreshTokenRequest request = new RefreshTokenRequest(incomingToken);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("rt:" + incomingToken)).willReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);

        verify(userRepository, never()).findById(any(UUID.class));
        verify(jwtProvider, never()).generateAccessToken(any(UserDetails.class));
    }

    @Test
    void refreshToken_UserNotFoundInDb_ThrowsAppException() {
        // Given
        String incomingToken = "valid_refresh_token";
        RefreshTokenRequest request = new RefreshTokenRequest(incomingToken);
        UUID userId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("rt:" + incomingToken)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(jwtProvider, never()).generateAccessToken(any(UserDetails.class));
    }

    @Test
    void refreshToken_NullRequest_ThrowsNullPointerException() {
        // Given
        RefreshTokenRequest request = null;

        // When & Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(NullPointerException.class);

        verify(redisTemplate, never()).opsForValue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: logout(String refreshToken)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void logout_ValidToken_DeletesFromRedis() {
        // Given
        String refreshToken = "valid_refresh_token";

        // When
        authService.logout(refreshToken);

        // Then
        verify(redisTemplate, times(1)).delete("rt:" + refreshToken);
    }

    @Test
    void logout_NullToken_DoesNothing() {
        // Given
        String refreshToken = null;

        // When
        authService.logout(refreshToken);

        // Then
        verify(redisTemplate, never()).delete(anyString());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private RegisterRequest createMockRegisterRequest() {
        return new RegisterRequest("Tuan Anh Vu", "tuananh@example.com", "strongpassword123");
    }

    private Role createMockRole() {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("ROLE_USER");
        return role;
    }

    private User createMockUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setPasswordHash("hashed_password_123");
        return user;
    }
}
