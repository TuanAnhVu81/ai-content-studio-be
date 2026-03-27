package com.portfolio.aicontentstudio.modules.ai_log.service;

import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class AiUsageLogAsyncWriter {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;
    private final ContentRepository contentRepository;

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistUsage(AiUsageLogCommand command) {
        try {
            User userReference = userRepository.getReferenceById(command.userId());
            Content contentReference = command.contentId() != null
                    ? contentRepository.getReferenceById(command.contentId())
                    : null;
            int safePromptTokens = command.promptTokens() != null ? command.promptTokens() : 0;
            int safeResponseTokens = command.responseTokens() != null ? command.responseTokens() : 0;
            int safeTotalTokens = command.totalTokens() != null
                    ? command.totalTokens()
                    : safePromptTokens + safeResponseTokens;

            AiUsageLog usageLog = AiUsageLog.builder()
                    .user(userReference)
                    .content(contentReference)
                    .promptTokens(safePromptTokens)
                    .responseTokens(safeResponseTokens)
                    .totalTokens(safeTotalTokens)
                    .modelName(command.modelName())
                    .build();

            aiUsageLogRepository.save(usageLog);
            log.info("[AiBilling-Async] Logged {} tokens for contentId={}", usageLog.getTotalTokens(), command.contentId());
        } catch (Exception ex) {
            log.error("[AiBilling-Async] Failed to log usage for contentId={}", command.contentId(), ex);
        }
    }
}
