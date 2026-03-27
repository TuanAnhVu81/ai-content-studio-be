package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminCampaignResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminRecentContentResponse;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignMetadata;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;
import com.portfolio.aicontentstudio.modules.content.repository.CampaignContentCountView;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AdminSystemServiceImpl using JUnit 5, Mockito, and AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private AdminSystemServiceImpl adminSystemService;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getAllCampaigns(Pageable pageable)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getAllCampaigns_WithExistingCampaigns_ReturnsMappedPage() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Campaign campaign = createMockCampaign(userId);
        User owner = createMockUser(userId, "owner@example.com");

        given(campaignRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(campaign), pageable, 1));
        given(userRepository.findAllById(List.of(userId))).willReturn(List.of(owner));
        given(contentRepository.countByCampaignIds(List.of(campaign.getId())))
                .willReturn(List.of(createCampaignContentCountView(campaign.getId(), 3L)));

        // When
        Page<AdminCampaignResponse> result = adminSystemService.getAllCampaigns(pageable);

        // Then
        verify(campaignRepository, times(1)).findAll(pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).ownerEmail()).isEqualTo("owner@example.com");
        assertThat(result.getContent().get(0).contentCount()).isEqualTo(3L);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getRecentContents()
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getRecentContents_WithExistingContents_ReturnsMappedRecentList() {
        // Given
        Content content = createMockContent();
        given(contentRepository.findTop50ByOrderByCreatedAtDesc()).willReturn(List.of(content));

        // When
        List<AdminRecentContentResponse> result = adminSystemService.getRecentContents();

        // Then
        verify(contentRepository, times(1)).findTop50ByOrderByCreatedAtDesc();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).campaignName()).isEqualTo("Campaign A");
        assertThat(result.get(0).contentPreview()).startsWith("Generated copy");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: hardDeleteContent(UUID contentId, String reason)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void hardDeleteContent_ContentExists_DeletesAndWritesAuditLog() {
        // Given
        UUID adminId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createMockContent();
        content.setId(contentId);

        given(securityContextHelper.getCurrentUserId()).willReturn(adminId);
        given(contentRepository.findWithCampaignAndUserById(contentId)).willReturn(Optional.of(content));

        // When
        adminSystemService.hardDeleteContent(contentId, "Sensitive violation");

        // Then
        verify(contentRepository, times(1)).hardDeleteById(contentId);
        verify(adminAuditLogService, times(1)).logAction(adminId, "HARD_DELETE_CONTENT", contentId, "Sensitive violation");
    }

    @Test
    void hardDeleteContent_ContentNotFound_ThrowsAppException() {
        // Given
        UUID contentId = UUID.randomUUID();
        given(securityContextHelper.getCurrentUserId()).willReturn(UUID.randomUUID());
        given(contentRepository.findWithCampaignAndUserById(contentId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> adminSystemService.hardDeleteContent(contentId, "Reason"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENT_NOT_FOUND);

        verify(contentRepository, never()).hardDeleteById(any(UUID.class));
        verify(adminAuditLogService, never()).logAction(any(UUID.class), any(String.class), any(UUID.class), any(String.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private Campaign createMockCampaign(UUID userId) {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setUserId(userId);
        campaign.setName("Campaign A");
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setMetadata(new CampaignMetadata("Goal", "Audience"));
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        return campaign;
    }

    private Content createMockContent() {
        UUID ownerId = UUID.randomUUID();
        Campaign campaign = createMockCampaign(ownerId);
        User user = createMockUser(ownerId, "owner@example.com");

        Content content = new Content();
        content.setId(UUID.randomUUID());
        content.setCampaign(campaign);
        content.setUser(user);
        content.setTargetKeyword("seo");
        content.setGeneratedText("Generated copy for admin dashboard verification and preview.");
        content.setBannerUrl("https://cdn.example.com/banner.jpg");
        content.setStatus(ContentStatus.DRAFT);
        content.setCreatedAt(LocalDateTime.now());
        return content;
    }

    private User createMockUser(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Owner User");
        return user;
    }

    private CampaignContentCountView createCampaignContentCountView(UUID campaignId, long contentCount) {
        return new CampaignContentCountView() {
            @Override
            public UUID getCampaignId() {
                return campaignId;
            }

            @Override
            public long getContentCount() {
                return contentCount;
            }
        };
    }
}
