package com.portfolio.aicontentstudio.modules.dashboard.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.dashboard.dto.UserDashboardResponse;
import com.portfolio.aicontentstudio.modules.dashboard.service.UserDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserDashboardService userDashboardService;

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserDashboardResponse>> getUserDashboard() {
        UserDashboardResponse response = userDashboardService.getUserDashboard();
        return ResponseEntity.ok(ApiResponse.success("User dashboard data retrieved successfully", response));
    }
}
