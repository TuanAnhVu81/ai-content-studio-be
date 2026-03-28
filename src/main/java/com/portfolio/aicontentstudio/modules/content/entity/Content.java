package com.portfolio.aicontentstudio.modules.content.entity;

import com.portfolio.aicontentstudio.core.entity.BaseEntity;
import com.portfolio.aicontentstudio.modules.content.dto.BannerConfig;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.content.dto.PromptConfig;
import com.portfolio.aicontentstudio.modules.content.dto.SeoMetadata;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Content entity storing AI generated text and metadata with JSONB.
 * Implements Soft Delete using Hibernate 6.
 */
@Entity
@Table(name = "contents")
@SQLDelete(sql = "UPDATE contents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Content extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_keyword", nullable = false)
    private String targetKeyword;

    @Type(JsonBinaryType.class)
    @Column(name = "prompt_config", columnDefinition = "jsonb", nullable = false)
    private PromptConfig promptConfig;

    @Column(name = "generated_text", columnDefinition = "TEXT", nullable = false)
    private String generatedText;

    @Type(JsonBinaryType.class)
    @Column(name = "seo_metadata", columnDefinition = "jsonb")
    private SeoMetadata seoMetadata;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Type(JsonBinaryType.class)
    @Column(name = "banner_config", columnDefinition = "jsonb")
    private BannerConfig bannerConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
