package com.portfolio.aicontentstudio.modules.content.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.dto.GenerateContentRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateBannerRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateContentRequest;
import com.portfolio.aicontentstudio.modules.content.service.ContentService;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for AI content generation and content management.
 *
 * Key design points:
 * - All userId values are extracted server-side from JWT - never trusted from request.
 * - campaignId ownership is validated in the Service layer to prevent IDOR.
 */
@RestController
@RequestMapping("/api/v1/contents")
@RequiredArgsConstructor
@Tag(name = "Content", description = "AI Content Generation & Management APIs")
public class ContentController {

    private final ContentService contentService;
    private final SecurityContextHelper securityContextHelper;

    @PostMapping("/generate")
    @Operation(summary = "Generate AI content")
    public ResponseEntity<ApiResponse<ContentResponse>> generateContent(@Valid @RequestBody GenerateContentRequest request) {
        UUID userId = securityContextHelper.getCurrentUserId();
        ContentResponse response = contentService.generateContent(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Content generated successfully", response));
    }

    // =========================================================================
    // CONTENT CRUD 
    // =========================================================================

    /**
     * GET /api/v1/contents?campaignId={id}&page=0&size=10&sort=createdAt,desc
     * Returns paginated list of contents belonging to a campaign.
     * IDOR protected: validates campaign ownership before querying.
     */
    @GetMapping
    @Operation(summary = "Get contents by campaign (paginated)")
    public ResponseEntity<ApiResponse<Page<ContentResponse>>> getContentsByCampaign(
            @RequestParam("campaignId") UUID campaignId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        UUID userId = securityContextHelper.getCurrentUserId();
        Page<ContentResponse> page = contentService.getContentsByCampaign(campaignId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Contents fetched successfully", page));
    }

    /**
     * GET /api/v1/contents/{id}
     * Returns details of a single content item.
     * IDOR protected: only the owner can access.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get content by ID")
    public ResponseEntity<ApiResponse<ContentResponse>> getContentById(@PathVariable("id") UUID id) {
        UUID userId = securityContextHelper.getCurrentUserId();
        ContentResponse response = contentService.getContentById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Content fetched successfully", response));
    }

    /**
     * PUT /api/v1/contents/{id}
     * Allows the user to save manually edited text and SEO metadata from the Rich Text Editor.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update content text and SEO metadata")
    public ResponseEntity<ApiResponse<ContentResponse>> updateContent(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateContentRequest request) {

        UUID userId = securityContextHelper.getCurrentUserId();
        ContentResponse response = contentService.updateContent(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Content updated successfully", response));
    }

    /**
     * PUT /api/v1/contents/{id}/banner
     * Saves the Cloudinary banner image URL provided by the Frontend after its direct upload.
     */
    @PutMapping("/{id}/banner")
    @Operation(summary = "Update banner image URL",
               description = "Frontend uploads image directly to Cloudinary, then sends the resulting URL here")
    public ResponseEntity<ApiResponse<ContentResponse>> updateBanner(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateBannerRequest request) {

        UUID userId = securityContextHelper.getCurrentUserId();
        ContentResponse response = contentService.updateBanner(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Banner updated successfully", response));
    }

    /**
     * DELETE /api/v1/contents/{id}
     * Soft-delete a content article.
     * Only the owner of the campaign this content belongs to can delete it.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a content article")
    public ResponseEntity<Void> deleteContent(@PathVariable("id") UUID id) {
        UUID userId = securityContextHelper.getCurrentUserId();
        contentService.deleteContent(id, userId);
        return ResponseEntity.noContent().build(); // HTTP 204
    }
}
