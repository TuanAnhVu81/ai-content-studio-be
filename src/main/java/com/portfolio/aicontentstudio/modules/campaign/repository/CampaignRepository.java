package com.portfolio.aicontentstudio.modules.campaign.repository;

import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for Campaign.
 * All queries are scoped to a userId to enforce Data Isolation (prevent IDOR).
 */
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    // Paginated list - only returns campaigns belonging to a specific user
    Page<Campaign> findAllByUserId(UUID userId, Pageable pageable);

    // Filtered paginated list by status - scoped to user
    Page<Campaign> findAllByUserIdAndStatus(UUID userId, CampaignStatus status, Pageable pageable);

    // Find a campaign by id AND verify it belongs to the current user (prevents IDOR)
    Optional<Campaign> findByIdAndUserId(UUID id, UUID userId);

    // Check for duplicate name within the same user scope
    boolean existsByNameAndUserId(String name, UUID userId);

    // Check for duplicate name excluding the current record (for UPDATE operations)
    boolean existsByNameAndUserIdAndIdNot(String name, UUID userId, UUID id);

    // Ownership check used by Content Service before calling Gemini API (prevents IDOR)
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
