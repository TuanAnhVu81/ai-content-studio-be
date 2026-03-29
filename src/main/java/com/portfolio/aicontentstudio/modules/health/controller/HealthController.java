package com.portfolio.aicontentstudio.modules.health.controller;

import com.portfolio.aicontentstudio.core.dto.ApiResponse;
import com.portfolio.aicontentstudio.modules.health.dto.HealthCheckStatusResponse;
import com.portfolio.aicontentstudio.modules.health.dto.HealthLiveResponse;
import com.portfolio.aicontentstudio.modules.health.dto.HealthReadyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
/**
 * Public health endpoints for uptime monitors and readiness checks.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private static final String SERVICE_NAME = "ai-content-studio-be";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    @GetMapping("/live")
    public ResponseEntity<ApiResponse<HealthLiveResponse>> live() {
        HealthLiveResponse response = new HealthLiveResponse(
                "UP",
                SERVICE_NAME,
                resolveEnvironment(),
                LocalDateTime.now()
        );

        return ResponseEntity.ok(ApiResponse.success("Service is alive", response));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<HealthReadyResponse>> ready() {
        String databaseStatus = checkDatabase();
        String redisStatus = checkRedis();
        boolean ready = "UP".equals(databaseStatus) && "UP".equals(redisStatus);

        HealthReadyResponse response = new HealthReadyResponse(
                ready ? "UP" : "DOWN",
                SERVICE_NAME,
                resolveEnvironment(),
                LocalDateTime.now(),
                new HealthCheckStatusResponse(databaseStatus, redisStatus)
        );

        ApiResponse<HealthReadyResponse> body = ApiResponse.<HealthReadyResponse>builder()
                .status(ready ? "success" : "error")
                .message(ready ? "Service is ready" : "Service dependencies unavailable")
                .data(response)
                .build();

        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private String checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String resolveEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
    }
}
