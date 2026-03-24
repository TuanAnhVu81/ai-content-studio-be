package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.admin.entity.AdminAuditLog;
import com.portfolio.aicontentstudio.modules.admin.repository.AdminAuditLogRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AdminAuditLogServiceImpl using JUnit 5, Mockito, and AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceImplTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAuditLogServiceImpl adminAuditLogService;

    @Captor
    private ArgumentCaptor<AdminAuditLog> adminAuditLogCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: logAction(UUID adminId, String action, UUID targetId, String reason)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void logAction_AdminExists_SavesAuditLog() {
        // Given
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User admin = createMockUser(adminId);

        given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
        given(adminAuditLogRepository.save(any(AdminAuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        adminAuditLogService.logAction(adminId, "HARD_DELETE_CONTENT", targetId, "Spam content");

        // Then
        verify(adminAuditLogRepository, times(1)).save(adminAuditLogCaptor.capture());
        AdminAuditLog capturedAuditLog = adminAuditLogCaptor.getValue();

        assertThat(capturedAuditLog.getAdmin()).isEqualTo(admin);
        assertThat(capturedAuditLog.getAction()).isEqualTo("HARD_DELETE_CONTENT");
        assertThat(capturedAuditLog.getTargetId()).isEqualTo(targetId);
        assertThat(capturedAuditLog.getReason()).isEqualTo("Spam content");
    }

    @Test
    void logAction_AdminNotFound_ThrowsAppException() {
        // Given
        UUID adminId = UUID.randomUUID();

        given(userRepository.findById(adminId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> adminAuditLogService.logAction(adminId, "UPDATE_USER_STATUS", UUID.randomUUID(), "Reason"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(adminAuditLogRepository, never()).save(any(AdminAuditLog.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private User createMockUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("admin@example.com");
        user.setFullName("System Admin");
        return user;
    }
}
