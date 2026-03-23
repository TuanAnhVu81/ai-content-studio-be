package com.portfolio.aicontentstudio.modules.campaign.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignRequest;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import com.portfolio.aicontentstudio.modules.campaign.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for Campaign management.
 * All endpoints are protected by JWT authentication.
 * userId is ALWAYS extracted from SecurityContext, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    // POST /api/v1/campaigns - Create a new campaign
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @Valid @RequestBody CampaignRequest request) {
        CampaignResponse response = campaignService.createCampaign(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Campaign created successfully", response));
    }

    // GET /api/v1/campaigns?status=ACTIVE&page=0&size=10&sort=createdAt,desc
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CampaignResponse>>> getMyCampaigns(
            @RequestParam(value = "status", required = false) CampaignStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CampaignResponse> response = campaignService.getMyCampaigns(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Campaigns retrieved successfully", response));
    }

    // GET /api/v1/campaigns/{id} - Get a single campaign (ownership verified)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getCampaignById(@PathVariable("id") UUID id) {
        CampaignResponse response = campaignService.getCampaignById(id);
        return ResponseEntity.ok(ApiResponse.success("Campaign retrieved successfully", response));
    }

    // PUT /api/v1/campaigns/{id} - Update a campaign (ownership verified)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> updateCampaign(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CampaignRequest request) {
        CampaignResponse response = campaignService.updateCampaign(id, request);
        return ResponseEntity.ok(ApiResponse.success("Campaign updated successfully", response));
    }

    // DELETE /api/v1/campaigns/{id} - Soft delete (ownership verified)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable("id") UUID id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build(); // HTTP 204
    }
}
