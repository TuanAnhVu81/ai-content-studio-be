package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageAggregate;
import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageStatsResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final int TOP_USERS_LIMIT = 10;

    private final AiUsageLogRepository aiUsageLogRepository;

    @Override
    @Transactional(readOnly = true)
    public AiUsageStatsResponse getAiUsageStats(LocalDateTime from, LocalDateTime to) {
        AiUsageAggregate aggregate = aiUsageLogRepository.aggregateUsage(from, to);
        AiUsageAggregate safeAggregate = aggregate != null ? aggregate : new AiUsageAggregate(0L, 0L, 0L);

        return new AiUsageStatsResponse(
                from,
                to,
                safeAggregate.totalPromptTokens(),
                safeAggregate.totalResponseTokens(),
                safeAggregate.totalTokens(),
                aiUsageLogRepository.aggregateUsageByModel(from, to)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopUserUsageResponse> getTopUsers(LocalDateTime from, LocalDateTime to) {
        return aiUsageLogRepository.findTopUsersByTokenUsage(from, to, PageRequest.of(0, TOP_USERS_LIMIT));
    }
}
