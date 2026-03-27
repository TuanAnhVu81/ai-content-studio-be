package com.portfolio.aicontentstudio.modules.ai_log.service;

import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageLogService {

    private final AiUsageLogAsyncWriter aiUsageLogAsyncWriter;

    public void logUsage(User user, Content content, Integer promptTokens, Integer responseTokens, Integer totalTokens, String modelName) {
        if (user == null || user.getId() == null) {
            log.warn("[AiBilling-Async] Skipping usage logging because user reference is missing");
            return;
        }

        AiUsageLogCommand command = new AiUsageLogCommand(
                user.getId(),
                content != null ? content.getId() : null,
                promptTokens,
                responseTokens,
                totalTokens,
                modelName
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiUsageLogAsyncWriter.persistUsage(command);
                }
            });
            return;
        }

        aiUsageLogAsyncWriter.persistUsage(command);
    }
}
