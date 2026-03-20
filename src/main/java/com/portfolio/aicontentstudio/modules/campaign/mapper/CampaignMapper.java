package com.portfolio.aicontentstudio.modules.campaign.mapper;

import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between Campaign entity and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CampaignMapper {

    // MapStruct will automatically map fields with matching names:
    // id, name, status, metadata, createdAt, updatedAt
    CampaignResponse toResponse(Campaign campaign);
}
