package com.portfolio.aicontentstudio.modules.campaign.entity;

import com.portfolio.aicontentstudio.core.entity.BaseEntity;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Campaign entity for grouping content articles.
 */
@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(name = "target_audience")
    private String targetAudience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
