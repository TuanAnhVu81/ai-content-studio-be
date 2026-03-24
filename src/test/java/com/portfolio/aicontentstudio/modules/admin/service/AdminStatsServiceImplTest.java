package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageAggregate;
import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageStatsResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.ModelUsageResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AdminStatsServiceImpl using JUnit 5, Mockito, and AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class AdminStatsServiceImplTest {

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @InjectMocks
    private AdminStatsServiceImpl adminStatsService;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getAiUsageStats(LocalDateTime from, LocalDateTime to)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getAiUsageStats_WithAggregatedData_ReturnsUsageStatistics() {
        // Given
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 31, 23, 59);
        List<ModelUsageResponse> modelUsage = List.of(new ModelUsageResponse("gemini-2.5-flash", 1500L));

        given(aiUsageLogRepository.aggregateUsage(from, to)).willReturn(new AiUsageAggregate(500L, 1000L, 1500L));
        given(aiUsageLogRepository.aggregateUsageByModel(from, to)).willReturn(modelUsage);

        // When
        AiUsageStatsResponse result = adminStatsService.getAiUsageStats(from, to);

        // Then
        verify(aiUsageLogRepository, times(1)).aggregateUsage(from, to);
        verify(aiUsageLogRepository, times(1)).aggregateUsageByModel(from, to);
        assertThat(result.totalTokens()).isEqualTo(1500L);
        assertThat(result.tokensByModel()).containsExactlyElementsOf(modelUsage);
    }

    @Test
    void getAiUsageStats_NoAggregateReturned_DefaultsToZeroTotals() {
        // Given
        given(aiUsageLogRepository.aggregateUsage(null, null)).willReturn(null);
        given(aiUsageLogRepository.aggregateUsageByModel(null, null)).willReturn(List.of());

        // When
        AiUsageStatsResponse result = adminStatsService.getAiUsageStats(null, null);

        // Then
        assertThat(result.totalPromptTokens()).isZero();
        assertThat(result.totalResponseTokens()).isZero();
        assertThat(result.totalTokens()).isZero();
        assertThat(result.tokensByModel()).isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getTopUsers(LocalDateTime from, LocalDateTime to)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getTopUsers_WithRepositoryResults_ReturnsTopTenUsers() {
        // Given
        List<TopUserUsageResponse> topUsers = List.of(
                new TopUserUsageResponse(UUID.randomUUID(), "user@example.com", "User", 2000L, 700L, 1300L)
        );

        given(aiUsageLogRepository.findTopUsersByTokenUsage(null, null, PageRequest.of(0, 10))).willReturn(topUsers);

        // When
        List<TopUserUsageResponse> result = adminStatsService.getTopUsers(null, null);

        // Then
        verify(aiUsageLogRepository, times(1)).findTopUsersByTokenUsage(null, null, PageRequest.of(0, 10));
        assertThat(result).containsExactlyElementsOf(topUsers);
    }
}
