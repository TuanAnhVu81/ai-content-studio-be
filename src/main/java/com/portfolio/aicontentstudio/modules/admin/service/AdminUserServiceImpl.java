package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.service.RefreshTokenSessionService;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminUserResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.UpdateUserStatusRequest;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private static final String ACTION_UPDATE_USER_STATUS = "UPDATE_USER_STATUS";

    private final UserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AdminAuditLogService adminAuditLogService;
    private final RefreshTokenSessionService refreshTokenSessionService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String email, AccountStatus status, Pageable pageable) {
        String normalizedEmail = normalizeEmailFilter(email);
        Page<User> users;

        if (normalizedEmail == null && status == null) {
            users = userRepository.findAll(pageable);
        } else if (normalizedEmail == null) {
            users = userRepository.findAllByStatus(status, pageable);
        } else if (status == null) {
            users = userRepository.searchUsersByEmailForAdmin(normalizedEmail, pageable);
        } else {
            users = userRepository.searchUsersByEmailAndStatusForAdmin(normalizedEmail, status, pageable);
        }

        return users.map(this::toResponse);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        UUID currentAdminId = securityContextHelper.getCurrentUserId();
        String normalizedReason = normalizeReason(request.reason());

        if (currentAdminId.equals(userId) && request.status() == AccountStatus.INACTIVE) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Admin cannot deactivate the current account");
        }

        if (normalizedReason == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "reason is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setStatus(request.status());
        User savedUser = userRepository.save(user);
        if (request.status() == AccountStatus.INACTIVE) {
            refreshTokenSessionService.revokeAllSessions(savedUser.getId());
        }
        adminAuditLogService.logAction(currentAdminId, ACTION_UPDATE_USER_STATUS, savedUser.getId(), normalizedReason);

        log.info("Admin updated user status: adminId={}, targetUserId={}, status={}", currentAdminId, userId, request.status());
        return toResponse(savedUser);
    }

    private AdminUserResponse toResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeEmailFilter(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
