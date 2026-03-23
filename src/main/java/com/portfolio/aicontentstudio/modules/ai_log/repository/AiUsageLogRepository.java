package com.portfolio.aicontentstudio.modules.ai_log.repository;

import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for AI usage log tracking and billing audits.
 */
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {
}
