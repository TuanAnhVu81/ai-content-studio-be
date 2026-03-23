package com.portfolio.aicontentstudio.modules.content.mapper;

import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Content entity <-> DTO conversion.
 * Extracts nested IDs from JPA relationships using expressions.
 */
@Mapper(componentModel = "spring")
public interface ContentMapper {

    // Map campaign.id and user.id from lazy-loaded JPA relations to flat UUIDs in DTO
    @Mapping(target = "campaignId", expression = "java(content.getCampaign().getId())")
    ContentResponse toResponse(Content content);
}
