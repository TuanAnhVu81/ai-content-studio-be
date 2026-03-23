package com.portfolio.aicontentstudio.modules.ai_log.service;

import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageLogService {

    private final AiUsageLogRepository aiUsageLogRepository;

    @Async("taskExecutor")
    public void logUsage(User user, Content content, Integer promptTokens, Integer responseTokens, Integer totalTokens, String modelName) {
        try {
            AiUsageLog usageLog = AiUsageLog.builder()
                    .user(user)
                    .content(content)
                    .promptTokens(promptTokens != null ? promptTokens : 0)
                    .responseTokens(responseTokens != null ? responseTokens : 0)
                    .totalTokens(totalTokens != null ? totalTokens : 0)
                    .modelName(modelName)
                    .build();

            aiUsageLogRepository.save(usageLog);
            log.info("[AiBilling-Async] Logged {} tokens for contentId={}", usageLog.getTotalTokens(), content.getId());
        } catch (Exception ex) {
            log.error("[AiBilling-Async] Failed to log usage for contentId={}: {}", content.getId(), ex.getMessage());
        }
    }
}
