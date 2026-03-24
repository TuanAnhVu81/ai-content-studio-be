package com.portfolio.aicontentstudio.modules.admin.service;

import java.util.UUID;

public interface AdminAuditLogService {

    void logAction(UUID adminId, String action, UUID targetId, String reason);
}
