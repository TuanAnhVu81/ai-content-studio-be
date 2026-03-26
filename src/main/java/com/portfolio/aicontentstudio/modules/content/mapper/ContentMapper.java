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

    // Flatten nested entity fields into DTO
    @Mapping(target = "campaignId", expression = "java(content.getCampaign().getId())")
    @Mapping(target = "campaignName", expression = "java(content.getCampaign().getName())")
    ContentResponse toResponse(Content content);
}
