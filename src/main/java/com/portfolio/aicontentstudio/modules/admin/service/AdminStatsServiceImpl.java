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
        // Normalize nullable date params before hitting repository to enable index usage
        LocalDateTime resolvedFrom = from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();

        AiUsageAggregate aggregate = aiUsageLogRepository.aggregateUsage(resolvedFrom, resolvedTo);
        AiUsageAggregate safeAggregate = aggregate != null ? aggregate : new AiUsageAggregate(0L, 0L, 0L);

        return new AiUsageStatsResponse(
                from,
                to,
                safeAggregate.totalPromptTokens(),
                safeAggregate.totalResponseTokens(),
                safeAggregate.totalTokens(),
                aiUsageLogRepository.aggregateUsageByModel(resolvedFrom, resolvedTo)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopUserUsageResponse> getTopUsers(LocalDateTime from, LocalDateTime to) {
        // Normalize nullable date params before hitting repository to enable index usage
        LocalDateTime resolvedFrom = from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        return aiUsageLogRepository.findTopUsersByTokenUsage(resolvedFrom, resolvedTo, PageRequest.of(0, TOP_USERS_LIMIT));
    }
}
