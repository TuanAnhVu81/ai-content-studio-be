package com.portfolio.aicontentstudio.modules.content.repository;

import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.dashboard.dto.RecentContentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Content module.
 * All queries are scoped to userId to enforce strict Data Isolation.
 */
public interface ContentRepository extends JpaRepository<Content, UUID> {

    // Fetch all content belonging to a specific campaign owned by the user (IDOR-safe)
    Page<Content> findAllByCampaignIdAndUserId(UUID campaignId, UUID userId, Pageable pageable);

    @Query("""
            select new com.portfolio.aicontentstudio.modules.dashboard.dto.RecentContentSummaryResponse(
                c.id,
                campaign.id,
                campaign.name,
                c.targetKeyword,
                c.status,
                c.createdAt,
                c.updatedAt
            )
            from Content c
            join c.campaign campaign
            where c.user.id = :userId
            order by c.createdAt desc
            """)
    List<RecentContentSummaryResponse> findRecentContentSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select c.campaign.id as campaignId, count(c) as contentCount
            from Content c
            where c.campaign.id in :campaignIds
            group by c.campaign.id
            """)
    List<CampaignContentCountView> countByCampaignIds(@Param("campaignIds") List<UUID> campaignIds);

    long countByUserId(UUID userId);

    // Fetch a single content item ensuring it belongs to the requesting user
    Optional<Content> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"campaign", "user"})
    List<Content> findTop50ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"campaign", "user"})
    Optional<Content> findWithCampaignAndUserById(UUID id);

    @Modifying
    @Query("delete from Content c where c.id = :id")
    void hardDeleteById(@Param("id") UUID id);
}
