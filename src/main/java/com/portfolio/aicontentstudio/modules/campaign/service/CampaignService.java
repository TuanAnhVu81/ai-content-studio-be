package com.portfolio.aicontentstudio.modules.campaign.service;

import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignRequest;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contract for Campaign business logic.
 */
public interface CampaignService {

    // Create a new campaign for the authenticated user
    CampaignResponse createCampaign(CampaignRequest request);

    // Get paginated campaigns belonging to the authenticated user (optional status filter)
    Page<CampaignResponse> getMyCampaigns(CampaignStatus status, Pageable pageable);

    // Get a single campaign by ID - validates ownership
    CampaignResponse getCampaignById(UUID id);

    // Update a campaign - validates ownership and name uniqueness
    CampaignResponse updateCampaign(UUID id, CampaignRequest request);

    // Soft-delete a campaign - validates ownership
    void deleteCampaign(UUID id);
}
