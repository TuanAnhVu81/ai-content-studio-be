package com.portfolio.aicontentstudio.modules.campaign.entity;

import com.portfolio.aicontentstudio.core.entity.BaseEntity;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignMetadata;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Campaign entity: groups content articles under one marketing campaign.
 * Implements Soft Delete via deleted_at and JSONB metadata for flexible context.
 */
@Entity
@Table(
        name = "campaigns",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_campaign_name_user",
                columnNames = {"name", "user_id"}
        )
)
// Soft-delete: set deleted_at instead of physically removing the row
@SQLDelete(sql = "UPDATE campaigns SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign extends BaseEntity {

    @Column(nullable = false)
    private String name;

    // Campaign status lifecycle: DRAFT -> ACTIVE -> ARCHIVED
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "campaign_status")
    @Builder.Default
    private CampaignStatus status = CampaignStatus.ACTIVE;

    // JSONB column holds flexible campaign context (goal, target_audience)
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private CampaignMetadata metadata;

    // FK to the owning user (Data Isolation anchor)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Soft delete timestamp - null means the record is active
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
