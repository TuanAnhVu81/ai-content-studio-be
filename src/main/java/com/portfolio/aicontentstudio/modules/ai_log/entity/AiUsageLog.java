package com.portfolio.aicontentstudio.modules.ai_log.entity;

import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Usage logs for AI API tracking (tokens, models).
 */
@Entity
@Table(name = "ai_usage_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @Column(name = "prompt_tokens")
    @Builder.Default
    private Integer promptTokens = 0;

    @Column(name = "response_tokens")
    @Builder.Default
    private Integer responseTokens = 0;

    @Column(name = "total_tokens")
    @Builder.Default
    private Integer totalTokens = 0;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
