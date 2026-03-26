package com.portfolio.aicontentstudio.modules.dashboard.service;

import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.dashboard.dto.RecentContentSummaryResponse;
import com.portfolio.aicontentstudio.modules.dashboard.dto.UserDashboardResponse;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration Test for UserDashboardServiceImpl using JUnit 5, Mockito, and AssertJ.
 * Focuses on dashboard data aggregation and recent content summary projections.
 */
@ExtendWith(MockitoExtension.class)
class UserDashboardServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private UserDashboardServiceImpl dashboardService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getUserDashboard()
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getUserDashboard_WithRecentContents_ReturnsLightweightDashboardPayload() {
        // Given
        UUID userId = UUID.randomUUID();
        List<RecentContentSummaryResponse> recentContents = List.of(
                createRecentContentSummary("Campaign B", "seo-audit", LocalDateTime.now().minusHours(1)),
                createRecentContentSummary("Campaign A", "spring-security", LocalDateTime.now().minusHours(2))
        );

        given(securityContextHelper.getCurrentUserId()).willReturn(userId);
        given(campaignRepository.countByUserId(userId)).willReturn(3L);
        given(contentRepository.countByUserId(userId)).willReturn(12L);
        given(aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(eq(userId), any(LocalDateTime.class)))
                .willReturn(4500L);
        given(contentRepository.findRecentContentSummariesByUserId(eq(userId), any(Pageable.class)))
                .willReturn(recentContents);

        // When
        UserDashboardResponse response = dashboardService.getUserDashboard();

        // Then
        assertThat(response.totalCampaigns()).isEqualTo(3L);
        assertThat(response.totalContents()).isEqualTo(12L);
        assertThat(response.totalTokensUsed30Days()).isEqualTo(4500L);
        assertThat(response.recentContents()).hasSize(2);
        assertThat(response.recentContents().get(0).campaignName()).isEqualTo("Campaign B");
        assertThat(response.recentContents().get(0).targetKeyword()).isEqualTo("seo-audit");

        verify(contentRepository, times(1)).findRecentContentSummariesByUserId(eq(userId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().toString()).isEqualTo("UNSORTED");
    }

    @Test
    void getUserDashboard_WhenTokenSumIsNull_NormalizesTotalTokensToZero() {
        // Given
        UUID userId = UUID.randomUUID();

        given(securityContextHelper.getCurrentUserId()).willReturn(userId);
        given(campaignRepository.countByUserId(userId)).willReturn(1L);
        given(contentRepository.countByUserId(userId)).willReturn(2L);
        given(aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(eq(userId), any(LocalDateTime.class)))
                .willReturn(null);
        given(contentRepository.findRecentContentSummariesByUserId(eq(userId), any(Pageable.class)))
                .willReturn(List.of(createRecentContentSummary("Campaign A", "jwt-cookie", LocalDateTime.now())));

        // When
        UserDashboardResponse response = dashboardService.getUserDashboard();

        // Then
        assertThat(response.totalTokensUsed30Days()).isZero();
    }

    @Test
    void getUserDashboard_WhenUserHasNoData_ReturnsEmptyDashboard() {
        // Given
        UUID userId = UUID.randomUUID();

        given(securityContextHelper.getCurrentUserId()).willReturn(userId);
        given(campaignRepository.countByUserId(userId)).willReturn(0L);
        given(contentRepository.countByUserId(userId)).willReturn(0L);
        given(aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(eq(userId), any(LocalDateTime.class)))
                .willReturn(null);
        given(contentRepository.findRecentContentSummariesByUserId(eq(userId), any(Pageable.class)))
                .willReturn(List.of());

        // When
        UserDashboardResponse response = dashboardService.getUserDashboard();

        // Then
        assertThat(response.totalCampaigns()).isZero();
        assertThat(response.totalContents()).isZero();
        assertThat(response.totalTokensUsed30Days()).isZero();
        assertThat(response.recentContents()).isEmpty();
    }

    @Test
    void getUserDashboard_RecentContentsAlreadyProjected_PreservesRepositoryOrder() {
        // Given
        UUID userId = UUID.randomUUID();
        RecentContentSummaryResponse newest = createRecentContentSummary("Campaign New", "fresh-post", LocalDateTime.now());
        RecentContentSummaryResponse older = createRecentContentSummary("Campaign Old", "legacy-post", LocalDateTime.now().minusDays(1));

        given(securityContextHelper.getCurrentUserId()).willReturn(userId);
        given(campaignRepository.countByUserId(userId)).willReturn(2L);
        given(contentRepository.countByUserId(userId)).willReturn(8L);
        given(aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(eq(userId), any(LocalDateTime.class)))
                .willReturn(900L);
        given(contentRepository.findRecentContentSummariesByUserId(eq(userId), any(Pageable.class)))
                .willReturn(List.of(newest, older));

        // When
        UserDashboardResponse response = dashboardService.getUserDashboard();

        // Then
        assertThat(response.recentContents())
                .extracting(RecentContentSummaryResponse::targetKeyword)
                .containsExactly("fresh-post", "legacy-post");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // PRIVATE HELPERS & DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private RecentContentSummaryResponse createRecentContentSummary(String campaignName,
                                                                   String targetKeyword,
                                                                   LocalDateTime createdAt) {
        return new RecentContentSummaryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                campaignName,
                targetKeyword,
                ContentStatus.DRAFT,
                createdAt,
                createdAt.plusMinutes(5)
        );
    }
}
