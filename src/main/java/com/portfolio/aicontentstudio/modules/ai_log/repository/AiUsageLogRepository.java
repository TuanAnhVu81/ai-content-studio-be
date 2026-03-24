package com.portfolio.aicontentstudio.modules.ai_log.repository;

import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageAggregate;
import com.portfolio.aicontentstudio.modules.admin.dto.ModelUsageResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse;
import org.springframework.data.domain.Pageable;
import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI usage log tracking and billing audits.
 */
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {

    @Query("""
            select new com.portfolio.aicontentstudio.modules.admin.dto.AiUsageAggregate(
                coalesce(sum(l.promptTokens), 0L),
                coalesce(sum(l.responseTokens), 0L),
                coalesce(sum(l.totalTokens), 0L)
            )
            from AiUsageLog l
            where (cast(:from as localdatetime) is null or l.createdAt >= :from)
              and (cast(:to as localdatetime) is null or l.createdAt <= :to)
            """)
    AiUsageAggregate aggregateUsage(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Query("""
            select new com.portfolio.aicontentstudio.modules.admin.dto.ModelUsageResponse(
                coalesce(l.modelName, 'unknown'),
                coalesce(sum(l.totalTokens), 0L)
            )
            from AiUsageLog l
            where (cast(:from as localdatetime) is null or l.createdAt >= :from)
              and (cast(:to as localdatetime) is null or l.createdAt <= :to)
            group by coalesce(l.modelName, 'unknown')
            order by coalesce(sum(l.totalTokens), 0L) desc
            """)
    List<ModelUsageResponse> aggregateUsageByModel(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Query("""
            select new com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse(
                u.id,
                u.email,
                u.fullName,
                coalesce(sum(l.totalTokens), 0L),
                coalesce(sum(l.promptTokens), 0L),
                coalesce(sum(l.responseTokens), 0L)
            )
            from AiUsageLog l
            join l.user u
            where (cast(:from as localdatetime) is null or l.createdAt >= :from)
              and (cast(:to as localdatetime) is null or l.createdAt <= :to)
            group by u.id, u.email, u.fullName
            order by coalesce(sum(l.totalTokens), 0L) desc
            """)
    List<TopUserUsageResponse> findTopUsersByTokenUsage(@Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to,
                                                        Pageable pageable);

    @Query("""
            select coalesce(sum(l.totalTokens), 0L)
            from AiUsageLog l
            where l.user.id = :userId
              and l.createdAt >= :since
            """)
    long sumTotalTokensByUserIdInLast30Days(@Param("userId") UUID userId,
                                            @Param("since") LocalDateTime since);
}
