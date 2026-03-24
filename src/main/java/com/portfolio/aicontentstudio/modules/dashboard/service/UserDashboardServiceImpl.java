package com.portfolio.aicontentstudio.modules.dashboard.service;

import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.mapper.ContentMapper;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.dashboard.dto.UserDashboardResponse;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDashboardServiceImpl implements UserDashboardService {

    private final CampaignRepository campaignRepository;
    private final ContentRepository contentRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final ContentMapper contentMapper;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @Transactional(readOnly = true)
    public UserDashboardResponse getUserDashboard() {
        UUID userId = securityContextHelper.getCurrentUserId();
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        return new UserDashboardResponse(
                campaignRepository.countByUserId(userId),
                contentRepository.countByUserId(userId),
                aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(userId, thirtyDaysAgo),
                contentRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(contentMapper::toResponse)
                        .toList()
        );
    }
}
