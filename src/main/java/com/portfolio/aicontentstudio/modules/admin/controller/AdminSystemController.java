package com.portfolio.aicontentstudio.modules.admin.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminCampaignResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminRecentContentResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.HardDeleteContentRequest;
import com.portfolio.aicontentstudio.modules.admin.service.AdminSystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<Page<AdminCampaignResponse>>> getAllCampaigns(
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminCampaignResponse> response = adminSystemService.getAllCampaigns(pageable);
        return ResponseEntity.ok(ApiResponse.success("Campaigns retrieved successfully", response));
    }

    @GetMapping("/contents/recent")
    public ResponseEntity<ApiResponse<List<AdminRecentContentResponse>>> getRecentContents() {
        List<AdminRecentContentResponse> response = adminSystemService.getRecentContents();
        return ResponseEntity.ok(ApiResponse.success("Recent contents retrieved successfully", response));
    }

    @DeleteMapping("/contents/{id}")
    public ResponseEntity<Void> hardDeleteContent(
            @PathVariable("id") UUID id,
            @Valid @RequestBody HardDeleteContentRequest request) {

        adminSystemService.hardDeleteContent(id, request.reason());
        return ResponseEntity.noContent().build();
    }
}
