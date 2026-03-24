package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageStatsResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminStatsService {

    AiUsageStatsResponse getAiUsageStats(LocalDateTime from, LocalDateTime to);

    List<TopUserUsageResponse> getTopUsers(LocalDateTime from, LocalDateTime to);
}
