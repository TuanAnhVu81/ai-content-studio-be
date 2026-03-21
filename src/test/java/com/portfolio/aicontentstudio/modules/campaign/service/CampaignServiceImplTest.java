package com.portfolio.aicontentstudio.modules.campaign.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignMetadata;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignRequest;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import com.portfolio.aicontentstudio.modules.campaign.mapper.CampaignMapper;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
 * Pure Unit Test for CampaignServiceImpl using JUnit 5, Mockito, and AssertJ.
 * Focuses on full coverage and isolation.
 */
@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignMapper campaignMapper;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    @Captor
    private ArgumentCaptor<Campaign> campaignCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: createCampaign(CampaignRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void createCampaign_ValidRequest_ReturnsCampaignResponse() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        CampaignRequest request = createMockCampaignRequest();
        Campaign savedCampaign = createMockCampaign(mockUserId, request);
        CampaignResponse expectedResponse = createMockCampaignResponse(savedCampaign);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.existsByNameAndUserId(request.name(), mockUserId)).willReturn(false);
        given(campaignRepository.save(any(Campaign.class))).willReturn(savedCampaign);
        given(campaignMapper.toResponse(savedCampaign)).willReturn(expectedResponse);

        // When
        CampaignResponse actualResponse = campaignService.createCampaign(request);

        // Then
        verify(campaignRepository, times(1)).save(campaignCaptor.capture());
        Campaign capturedCampaign = campaignCaptor.getValue();
        
        assertThat(capturedCampaign.getName()).isEqualTo(request.name());
        assertThat(capturedCampaign.getUserId()).isEqualTo(mockUserId);
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void createCampaign_NameAlreadyExists_ThrowsAppException() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        CampaignRequest request = createMockCampaignRequest();

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.existsByNameAndUserId(request.name(), mockUserId)).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> campaignService.createCampaign(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAMPAIGN_NAME_DUPLICATE);

        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void createCampaign_NullRequest_ThrowsNullPointerException() {
        // Given
        CampaignRequest request = null;
        UUID mockUserId = UUID.randomUUID();
        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);

        // When & Then
        assertThatThrownBy(() -> campaignService.createCampaign(request))
                .isInstanceOf(NullPointerException.class);

        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getMyCampaigns(CampaignStatus status, Pageable pageable)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getMyCampaigns_WithStatusFilter_ReturnsFilteredPage() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        CampaignStatus status = CampaignStatus.ACTIVE;
        Pageable pageable = PageRequest.of(0, 10);
        Campaign campaign = createMockCampaign(mockUserId, createMockCampaignRequest());
        Page<Campaign> campaignPage = new PageImpl<>(List.of(campaign));
        CampaignResponse response = createMockCampaignResponse(campaign);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findAllByUserIdAndStatus(mockUserId, status, pageable)).willReturn(campaignPage);
        given(campaignMapper.toResponse(campaign)).willReturn(response);

        // When
        Page<CampaignResponse> result = campaignService.getMyCampaigns(status, pageable);

        // Then
        verify(campaignRepository, times(1)).findAllByUserIdAndStatus(mockUserId, status, pageable);
        verify(campaignRepository, never()).findAllByUserId(any(), any());
        
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(response);
    }

    @Test
    void getMyCampaigns_WithoutStatusFilter_ReturnsAllCampaignsPage() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Campaign campaign = createMockCampaign(mockUserId, createMockCampaignRequest());
        Page<Campaign> campaignPage = new PageImpl<>(List.of(campaign));
        CampaignResponse response = createMockCampaignResponse(campaign);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findAllByUserId(mockUserId, pageable)).willReturn(campaignPage);
        given(campaignMapper.toResponse(campaign)).willReturn(response);

        // When
        Page<CampaignResponse> result = campaignService.getMyCampaigns(null, pageable);

        // Then
        verify(campaignRepository, times(1)).findAllByUserId(mockUserId, pageable);
        verify(campaignRepository, never()).findAllByUserIdAndStatus(any(), any(), any());
        
        assertThat(result.getContent()).hasSize(1);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getCampaignById(UUID id)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getCampaignById_OwnedCampaign_ReturnsCampaignResponse() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = createMockCampaign(mockUserId, createMockCampaignRequest());
        CampaignResponse expectedResponse = createMockCampaignResponse(campaign);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.of(campaign));
        given(campaignMapper.toResponse(campaign)).willReturn(expectedResponse);

        // When
        CampaignResponse result = campaignService.getCampaignById(campaignId);

        // Then
        verify(campaignRepository, times(1)).findByIdAndUserId(campaignId, mockUserId);
        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void getCampaignById_NotOwnedOrNotFound_ThrowsAppException() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> campaignService.getCampaignById(campaignId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAMPAIGN_NOT_FOUND);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: updateCampaign(UUID id, CampaignRequest request)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void updateCampaign_ValidRequest_UpdatesAndReturnsResponse() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignRequest request = createMockCampaignRequest();
        Campaign existingCampaign = createMockCampaign(mockUserId, request);
        CampaignResponse expectedResponse = createMockCampaignResponse(existingCampaign);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.of(existingCampaign));
        given(campaignRepository.existsByNameAndUserIdAndIdNot(request.name(), mockUserId, campaignId)).willReturn(false);
        given(campaignRepository.save(existingCampaign)).willReturn(existingCampaign);
        given(campaignMapper.toResponse(existingCampaign)).willReturn(expectedResponse);

        // When
        CampaignResponse result = campaignService.updateCampaign(campaignId, request);

        // Then
        verify(campaignRepository, times(1)).save(campaignCaptor.capture());
        Campaign capturedCampaign = campaignCaptor.getValue();
        
        assertThat(capturedCampaign.getName()).isEqualTo(request.name());
        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void updateCampaign_DuplicateNameExcludeSelf_ThrowsAppException() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignRequest request = createMockCampaignRequest();
        Campaign existingCampaign = createMockCampaign(mockUserId, request);

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.of(existingCampaign));
        given(campaignRepository.existsByNameAndUserIdAndIdNot(request.name(), mockUserId, campaignId)).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> campaignService.updateCampaign(campaignId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAMPAIGN_NAME_DUPLICATE);

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void updateCampaign_NullRequest_ThrowsNullPointerException() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignRequest request = null;
        Campaign existingCampaign = createMockCampaign(mockUserId, createMockCampaignRequest());

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.of(existingCampaign));

        // When & Then
        assertThatThrownBy(() -> campaignService.updateCampaign(campaignId, request))
                .isInstanceOf(NullPointerException.class);

        verify(campaignRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: deleteCampaign(UUID id)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void deleteCampaign_OwnedCampaign_DeletesSuccessfully() {
        // Given
        UUID mockUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign existingCampaign = createMockCampaign(mockUserId, createMockCampaignRequest());

        given(securityContextHelper.getCurrentUserId()).willReturn(mockUserId);
        given(campaignRepository.findByIdAndUserId(campaignId, mockUserId)).willReturn(Optional.of(existingCampaign));

        // When
        campaignService.deleteCampaign(campaignId);

        // Then
        verify(campaignRepository, times(1)).delete(campaignCaptor.capture());
        Campaign capturedCampaign = campaignCaptor.getValue();
        assertThat(capturedCampaign).isEqualTo(existingCampaign);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private CampaignRequest createMockCampaignRequest() {
        return new CampaignRequest(
                "Summer Sale 2026",
                CampaignStatus.DRAFT,
                new CampaignMetadata("Boost sales", "Students")
        );
    }

    private Campaign createMockCampaign(UUID userId, CampaignRequest request) {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName(request.name());
        campaign.setStatus(request.status());
        campaign.setMetadata(request.metadata());
        campaign.setUserId(userId);
        return campaign;
    }

    private CampaignResponse createMockCampaignResponse(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getMetadata(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
