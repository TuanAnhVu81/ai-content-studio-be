package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.service.RefreshTokenSessionService;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminUserResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.UpdateUserStatusRequest;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AdminUserServiceImpl using JUnit 5, Mockito, and AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getUsers(String email, AccountStatus status, Pageable pageable)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getUsers_WithEmailAndStatusFilter_ReturnsPagedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        User user = createMockUser(UUID.randomUUID(), AccountStatus.ACTIVE);
        Page<User> userPage = new PageImpl<>(List.of(user));

        given(userRepository.searchUsersForAdmin("user@example.com", AccountStatus.ACTIVE, pageable)).willReturn(userPage);

        // When
        Page<AdminUserResponse> result = adminUserService.getUsers("user@example.com", AccountStatus.ACTIVE, pageable);

        // Then
        verify(userRepository, times(1)).searchUsersForAdmin("user@example.com", AccountStatus.ACTIVE, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).roles()).containsExactly("ROLE_USER");
    }

    @Test
    void getUsers_BlankEmailFilter_NormalizesToNull() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        given(userRepository.searchUsersForAdmin(null, null, pageable)).willReturn(Page.empty(pageable));

        // When
        Page<AdminUserResponse> result = adminUserService.getUsers("   ", null, pageable);

        // Then
        verify(userRepository, times(1)).searchUsersForAdmin(null, null, pageable);
        assertThat(result.getContent()).isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: updateUserStatus(UUID userId, UpdateUserStatusRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void updateUserStatus_ValidRequest_UpdatesUserAndWritesAuditLog() {
        // Given
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createMockUser(userId, AccountStatus.ACTIVE);
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(AccountStatus.INACTIVE, "Violation");

        given(securityContextHelper.getCurrentUserId()).willReturn(adminId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        AdminUserResponse result = adminUserService.updateUserStatus(userId, request);

        // Then
        verify(userRepository, times(1)).save(userCaptor.capture());
        verify(refreshTokenSessionService, times(1)).revokeAllSessions(userId);
        verify(adminAuditLogService, times(1)).logAction(adminId, "UPDATE_USER_STATUS", userId, "Violation");

        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(result.status()).isEqualTo(AccountStatus.INACTIVE);
    }

    @Test
    void updateUserStatus_TargetUserNotFound_ThrowsAppException() {
        // Given
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(AccountStatus.INACTIVE, "Reason");

        given(securityContextHelper.getCurrentUserId()).willReturn(adminId);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> adminUserService.updateUserStatus(userId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository, never()).save(any(User.class));
        verify(adminAuditLogService, never()).logAction(any(UUID.class), any(String.class), any(UUID.class), any(String.class));
        verify(refreshTokenSessionService, never()).revokeAllSessions(any(UUID.class));
    }

    @Test
    void updateUserStatus_AdminDeactivatesSelf_ThrowsAppException() {
        // Given
        UUID adminId = UUID.randomUUID();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(AccountStatus.INACTIVE, "Self lock");

        given(securityContextHelper.getCurrentUserId()).willReturn(adminId);

        // When
        // Then
        assertThatThrownBy(() -> adminUserService.updateUserStatus(adminId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(userRepository, never()).findById(any(UUID.class));
        verify(adminAuditLogService, never()).logAction(any(UUID.class), any(String.class), any(UUID.class), any(String.class));
        verify(refreshTokenSessionService, never()).revokeAllSessions(any(UUID.class));
    }

    @Test
    void updateUserStatus_BlankReason_ThrowsInvalidInput() {
        // Given
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(AccountStatus.INACTIVE, "   ");

        given(securityContextHelper.getCurrentUserId()).willReturn(adminId);

        // When
        // Then
        assertThatThrownBy(() -> adminUserService.updateUserStatus(userId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verify(userRepository, never()).findById(any(UUID.class));
        verify(userRepository, never()).save(any(User.class));
        verify(adminAuditLogService, never()).logAction(any(UUID.class), any(String.class), any(UUID.class), any(String.class));
        verify(refreshTokenSessionService, never()).revokeAllSessions(any(UUID.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private User createMockUser(UUID id, AccountStatus status) {
        Role role = Role.builder()
                .name("ROLE_USER")
                .build();

        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setFullName("Normal User");
        user.setStatus(status);
        user.setRoles(Set.of(role));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
