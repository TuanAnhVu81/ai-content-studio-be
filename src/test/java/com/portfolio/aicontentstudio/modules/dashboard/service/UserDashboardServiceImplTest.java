package com.portfolio.aicontentstudio.modules.dashboard.service;

import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.mapper.ContentMapper;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.dashboard.dto.UserDashboardResponse;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDashboardServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private UserDashboardServiceImpl dashboardService;

    @Test
    void getUserDashboard_AuthenticatedUser_ReturnsAggregatedData() {
        // Given
        UUID userId = UUID.randomUUID();
        given(securityContextHelper.getCurrentUserId()).willReturn(userId);
        
        given(campaignRepository.countByUserId(userId)).willReturn(10L);
        given(contentRepository.countByUserId(userId)).willReturn(50L);
        given(aiUsageLogRepository.sumTotalTokensByUserIdInLast30Days(eq(userId), any(LocalDateTime.class)))
                .willReturn(5000L);
        
        Content mockContent = new Content();
        ContentResponse mockResponse = mock(ContentResponse.class);
        given(contentRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId)).willReturn(List.of(mockContent));
        given(contentMapper.toResponse(mockContent)).willReturn(mockResponse);

        // When
        UserDashboardResponse response = dashboardService.getUserDashboard();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCampaigns()).isEqualTo(10L);
        assertThat(response.totalContents()).isEqualTo(50L);
        assertThat(response.totalTokensUsed30Days()).isEqualTo(5000L);
        assertThat(response.recentContents()).hasSize(1);
        
        verify(securityContextHelper).getCurrentUserId();
        verify(campaignRepository).countByUserId(userId);
        verify(contentRepository).countByUserId(userId);
        verify(aiUsageLogRepository).sumTotalTokensByUserIdInLast30Days(eq(userId), any());
    }
}
