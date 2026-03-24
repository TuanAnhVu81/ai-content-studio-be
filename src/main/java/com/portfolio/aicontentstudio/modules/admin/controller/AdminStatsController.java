package com.portfolio.aicontentstudio.modules.admin.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AiUsageStatsResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.TopUserUsageResponse;
import com.portfolio.aicontentstudio.modules.admin.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/ai-usage")
    public ResponseEntity<ApiResponse<AiUsageStatsResponse>> getAiUsageStats(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        AiUsageStatsResponse response = adminStatsService.getAiUsageStats(from, to);
        return ResponseEntity.ok(ApiResponse.success("AI usage statistics retrieved successfully", response));
    }

    @GetMapping("/top-users")
    public ResponseEntity<ApiResponse<List<TopUserUsageResponse>>> getTopUsers(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<TopUserUsageResponse> response = adminStatsService.getTopUsers(from, to);
        return ResponseEntity.ok(ApiResponse.success("Top AI usage users retrieved successfully", response));
    }
}
