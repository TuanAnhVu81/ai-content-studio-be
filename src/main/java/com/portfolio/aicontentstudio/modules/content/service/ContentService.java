package com.portfolio.aicontentstudio.modules.content.service;

import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.dto.GenerateContentRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateBannerRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateContentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contract for the AI Content generation and management service.
 */
public interface ContentService {

    ContentResponse generateContent(GenerateContentRequest request, UUID userId);

    /**
     * Get paginated list of contents for a campaign.
     * IDOR check: validates campaignId belongs to the authenticated userId.
     */
    Page<ContentResponse> getContentsByCampaign(UUID campaignId, UUID userId, Pageable pageable);

    /**
     * Get a single content item by ID, ensuring it belongs to the requesting user.
     */
    ContentResponse getContentById(UUID id, UUID userId);

    /**
     * Update the generated text and SEO metadata (after user manually edits in Rich Text Editor).
     */
    ContentResponse updateContent(UUID id, UpdateContentRequest request, UUID userId);

    /**
     * Update only the banner image URL (Cloudinary link sent by Frontend after upload).
     */
    ContentResponse updateBanner(UUID id, UpdateBannerRequest request, UUID userId);

    /**
     * Soft-delete a single content item.
     * IDOR check: only the owner can delete.
     */
    void deleteContent(UUID id, UUID userId);
}
