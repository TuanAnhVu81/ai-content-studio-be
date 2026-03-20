package com.portfolio.aicontentstudio.modules.campaign.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignRequest;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import com.portfolio.aicontentstudio.modules.campaign.mapper.CampaignMapper;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for Campaign management.
 * Strictly enforces Data Isolation: every operation is scoped to the current user's ID.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @Transactional
    public CampaignResponse createCampaign(CampaignRequest request) {
        UUID userId = securityContextHelper.getCurrentUserId();

        // Guard: reject duplicate campaign name for the same user
        if (campaignRepository.existsByNameAndUserId(request.name(), userId)) {
            throw new AppException(ErrorCode.CAMPAIGN_NAME_DUPLICATE);
        }

        Campaign campaign = Campaign.builder()
                .name(request.name())
                .status(request.status())
                .metadata(request.metadata())
                .userId(userId)
                .build();

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign created: id={}, userId={}", saved.getId(), userId);
        return campaignMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaignResponse> getMyCampaigns(CampaignStatus status, Pageable pageable) {
        UUID userId = securityContextHelper.getCurrentUserId();

        // If a status filter is provided, apply it; otherwise return all statuses
        Page<Campaign> campaigns = (status != null)
                ? campaignRepository.findAllByUserIdAndStatus(userId, status, pageable)
                : campaignRepository.findAllByUserId(userId, pageable);

        return campaigns.map(campaignMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResponse getCampaignById(UUID id) {
        Campaign campaign = findOwnedCampaign(id);
        return campaignMapper.toResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse updateCampaign(UUID id, CampaignRequest request) {
        UUID userId = securityContextHelper.getCurrentUserId();
        Campaign campaign = findOwnedCampaign(id);

        // Guard: reject duplicate name on UPDATE (exclude current record from check)
        if (campaignRepository.existsByNameAndUserIdAndIdNot(request.name(), userId, id)) {
            throw new AppException(ErrorCode.CAMPAIGN_NAME_DUPLICATE);
        }

        campaign.setName(request.name());
        campaign.setStatus(request.status());
        campaign.setMetadata(request.metadata());

        Campaign updated = campaignRepository.save(campaign);
        log.info("Campaign updated: id={}, userId={}", updated.getId(), userId);
        return campaignMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteCampaign(UUID id) {
        Campaign campaign = findOwnedCampaign(id);

        // Triggers @SQLDelete: sets deleted_at = NOW() instead of hard delete
        campaignRepository.delete(campaign);
        log.info("Campaign soft-deleted: id={}", id);
    }

    // Shared helper: load campaign and verify it belongs to the current user
    private Campaign findOwnedCampaign(UUID id) {
        UUID userId = securityContextHelper.getCurrentUserId();
        return campaignRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
