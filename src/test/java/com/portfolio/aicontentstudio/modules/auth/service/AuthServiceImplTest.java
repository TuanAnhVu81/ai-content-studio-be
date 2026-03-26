package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.JwtProvider;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AuthServiceImpl using JUnit 5, Mockito, and AssertJ.
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
    private UserDetailsService userDetailsService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

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

        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(defaultRole));
        given(passwordEncoder.encode(request.password())).willReturn("encoded_password");

        // When
        authService.register(request);

        // Then
        verify(userRepository, times(1)).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getEmail()).isEqualTo(request.email());
        assertThat(capturedUser.getFullName()).isEqualTo(request.fullName());
        assertThat(capturedUser.getPasswordHash()).isEqualTo("encoded_password");
        assertThat(capturedUser.getRoles()).containsExactly(defaultRole);
    }

    @Test
    void register_EmailAlreadyExists_ThrowsAppException() {
        // Given
        RegisterRequest request = createMockRegisterRequest();
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // When
        // Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any(User.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: login(LoginRequest request, ClientMetadata clientMetadata)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void login_ValidCredentials_ReturnsAccessTokenAndRefreshSession() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        ClientMetadata clientMetadata = new ClientMetadata("127.0.0.1", "JUnit");
        User user = createMockUser(AccountStatus.ACTIVE);
        UserDetails userDetails = mock(UserDetails.class);
        RefreshSessionResult refreshSessionResult = new RefreshSessionResult(UUID.randomUUID(), user.getId(), "new_refresh_token");

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(userDetailsService.loadUserByUsername(request.email())).willReturn(userDetails);
        given(jwtProvider.generateAccessToken(userDetails)).willReturn("access_token");
        given(refreshTokenSessionService.createSession(user.getId(), clientMetadata)).willReturn(refreshSessionResult);

        // When
        AuthSessionResult result = authService.login(request, clientMetadata);

        // Then
        verify(refreshTokenSessionService, times(1)).createSession(user.getId(), clientMetadata);
        assertThat(result.accessToken()).isEqualTo("access_token");
        assertThat(result.refreshToken()).isEqualTo("new_refresh_token");
        assertThat(result.user().email()).isEqualTo(user.getEmail());
    }

    @Test
    void login_InvalidCredentials_ThrowsAppException() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrong_password");

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("Invalid credentials"));

        // When
        // Then
        assertThatThrownBy(() -> authService.login(request, new ClientMetadata("127.0.0.1", "JUnit")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenSessionService, never()).createSession(any(UUID.class), any(ClientMetadata.class));
    }

    @Test
    void login_UserDisabled_ThrowsAppException() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new DisabledException("User disabled"));

        // When
        // Then
        assertThatThrownBy(() -> authService.login(request, new ClientMetadata("127.0.0.1", "JUnit")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_DISABLED);

        verify(refreshTokenSessionService, never()).createSession(any(UUID.class), any(ClientMetadata.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: refreshToken(String refreshToken, ClientMetadata clientMetadata)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void refreshToken_ValidSession_RotatesRefreshTokenAndReturnsAccessToken() {
        // Given
        User user = createMockUser(AccountStatus.ACTIVE);
        UserDetails userDetails = mock(UserDetails.class);
        ClientMetadata clientMetadata = new ClientMetadata("127.0.0.1", "JUnit");
        RefreshSessionResult refreshSessionResult = new RefreshSessionResult(UUID.randomUUID(), user.getId(), "rotated_refresh_token");

        given(refreshTokenSessionService.rotateSession("raw_refresh_token", clientMetadata)).willReturn(refreshSessionResult);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userDetailsService.loadUserByUsername(user.getEmail())).willReturn(userDetails);
        given(jwtProvider.generateAccessToken(userDetails)).willReturn("new_access_token");

        // When
        AuthSessionResult result = authService.refreshToken("raw_refresh_token", clientMetadata);

        // Then
        verify(refreshTokenSessionService, times(1)).rotateSession("raw_refresh_token", clientMetadata);
        assertThat(result.accessToken()).isEqualTo("new_access_token");
        assertThat(result.refreshToken()).isEqualTo("rotated_refresh_token");
    }

    @Test
    void refreshToken_UserDisabled_RevokesAllSessionsAndThrowsAppException() {
        // Given
        User user = createMockUser(AccountStatus.INACTIVE);
        ClientMetadata clientMetadata = new ClientMetadata("127.0.0.1", "JUnit");
        RefreshSessionResult refreshSessionResult = new RefreshSessionResult(UUID.randomUUID(), user.getId(), "rotated_refresh_token");

        given(refreshTokenSessionService.rotateSession("raw_refresh_token", clientMetadata)).willReturn(refreshSessionResult);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        // When
        // Then
        assertThatThrownBy(() -> authService.refreshToken("raw_refresh_token", clientMetadata))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_DISABLED);

        verify(refreshTokenSessionService, times(1)).revokeAllSessions(user.getId());
        verify(jwtProvider, never()).generateAccessToken(any(UserDetails.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: logout(String refreshToken)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void logout_RefreshTokenProvided_DelegatesToSessionService() {
        // Given
        String refreshToken = "raw_refresh_token";

        // When
        authService.logout(refreshToken);

        // Then
        verify(refreshTokenSessionService, times(1)).revokeCurrentSession(refreshToken);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getMe()
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getMe_AuthenticatedUser_ReturnsUserResponse() {
        // Given
        User user = createMockUser(AccountStatus.ACTIVE);
        given(securityContextHelper.getCurrentUserId()).willReturn(user.getId());
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        // When
        UserResponse response = authService.getMe();

        // Then
        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: changePassword(ChangePasswordRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void changePassword_ValidRequest_UpdatesPasswordAndRevokesAllSessions() {
        // Given
        User user = createMockUser(AccountStatus.ACTIVE);
        ChangePasswordRequest request = new ChangePasswordRequest("old_pass", "new_secure_pass");

        given(securityContextHelper.getCurrentUserId()).willReturn(user.getId());
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())).willReturn(true);
        given(passwordEncoder.encode(request.newPassword())).willReturn("new_hashed_pass");

        // When
        authService.changePassword(request);

        // Then
        verify(userRepository, times(1)).save(user);
        verify(refreshTokenSessionService, times(1)).revokeAllSessions(user.getId());
        assertThat(user.getPasswordHash()).isEqualTo("new_hashed_pass");
    }

    @Test
    void changePassword_InvalidCurrentPassword_ThrowsAppException() {
        // Given
        User user = createMockUser(AccountStatus.ACTIVE);
        ChangePasswordRequest request = new ChangePasswordRequest("wrong_old_pass", "new_secure_pass");

        given(securityContextHelper.getCurrentUserId()).willReturn(user.getId());
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())).willReturn(false);

        // When
        // Then
        assertThatThrownBy(() -> authService.changePassword(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verify(refreshTokenSessionService, never()).revokeAllSessions(any(UUID.class));
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

    private User createMockUser(AccountStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setPasswordHash("hashed_password_123");
        user.setStatus(status);
        user.setRoles(Set.of(createMockRole()));
        return user;
    }
}
