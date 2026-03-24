package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.admin.entity.AdminAuditLog;
import com.portfolio.aicontentstudio.modules.admin.repository.AdminAuditLogRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void logAction(UUID adminId, String action, UUID targetId, String reason) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        AdminAuditLog auditLog = AdminAuditLog.builder()
                .admin(admin)
                .action(action)
                .targetId(targetId)
                .reason(reason)
                .build();

        adminAuditLogRepository.save(auditLog);
        log.info("Admin audit log recorded: adminId={}, action={}, targetId={}", adminId, action, targetId);
    }
}
