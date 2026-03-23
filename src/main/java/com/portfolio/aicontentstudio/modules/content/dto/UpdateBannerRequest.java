package com.portfolio.aicontentstudio.modules.content.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating banner image URL.
 * Frontend uploads directly to Cloudinary SDK, then sends the resulting URL to this endpoint.
 */
public record UpdateBannerRequest(

        @NotBlank(message = "banner_url cannot be blank")
        String bannerUrl
) {}
