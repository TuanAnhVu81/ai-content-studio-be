package com.portfolio.aicontentstudio.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.aicontentstudio.config.properties.AuthSessionProperties;
import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionData;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    private RefreshTokenSessionServiceImpl refreshTokenSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthSessionProperties authSessionProperties = new AuthSessionProperties();
        authSessionProperties.setMaxActiveSessionsPerUser(5);
        authSessionProperties.setLockTimeoutSeconds(5);

        refreshTokenSessionService = new RefreshTokenSessionServiceImpl(redisTemplate, objectMapper, authSessionProperties);
        ReflectionTestUtils.setField(refreshTokenSessionService, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void rotateSession_ActiveTokenExists_RotatesTokenAndMarksOldTokenUsed() throws Exception {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String oldRefreshToken = "old-refresh-token";
        String oldTokenHash = hashToken(oldRefreshToken);
        RefreshSessionData sessionData = createActiveSessionData(sessionId, userId, oldTokenHash);
        String sessionJson = objectMapper.writeValueAsString(sessionData);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(valueOperations.get("auth:refresh:active:" + oldTokenHash)).willReturn(sessionId.toString(), sessionId.toString());
        given(valueOperations.get("auth:session:" + sessionId)).willReturn(sessionJson);
        given(valueOperations.setIfAbsent(eq("auth:session-lock:" + sessionId), anyString(), any(Duration.class))).willReturn(true);
        given(valueOperations.get("auth:session-lock:" + sessionId)).willReturn("different-lock-owner");
        given(zSetOperations.add(eq("auth:user-sessions:" + userId), eq(sessionId.toString()), anyDouble())).willReturn(true);
        given(redisTemplate.expire(eq("auth:user-sessions:" + userId), any(Duration.class))).willReturn(true);

        // When
        RefreshSessionResult result = refreshTokenSessionService.rotateSession(oldRefreshToken, new ClientMetadata("127.0.0.1", "JUnit"));

        // Then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(oldRefreshToken);

        verify(redisTemplate, times(1)).delete("auth:refresh:active:" + oldTokenHash);
        verify(valueOperations, atLeast(3)).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertThat(keyCaptor.getAllValues()).contains("auth:refresh:used:" + oldTokenHash);
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.startsWith("auth:refresh:active:") && !key.equals("auth:refresh:active:" + oldTokenHash));
    }

    @Test
    void rotateSession_ReusedOldToken_RevokesAllSessionsAndThrowsReuseDetected() throws Exception {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String oldRefreshToken = "reused-refresh-token";
        String oldTokenHash = hashToken(oldRefreshToken);
        RefreshSessionData sessionData = createActiveSessionData(sessionId, userId, oldTokenHash);
        String sessionJson = objectMapper.writeValueAsString(sessionData);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(valueOperations.get("auth:refresh:active:" + oldTokenHash)).willReturn(null);
        given(valueOperations.get("auth:refresh:used:" + oldTokenHash)).willReturn(sessionId.toString());
        given(valueOperations.get("auth:session:" + sessionId)).willReturn(sessionJson);
        given(zSetOperations.range("auth:user-sessions:" + userId, 0, -1)).willReturn(Set.of(sessionId.toString()));

        // When
        // Then
        assertThatThrownBy(() -> refreshTokenSessionService.rotateSession(oldRefreshToken, new ClientMetadata("127.0.0.1", "JUnit")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

        verify(redisTemplate, times(1)).delete("auth:user-sessions:" + userId);
        verify(zSetOperations, times(1)).remove("auth:user-sessions:" + userId, sessionId.toString());
    }

    @Test
    void revokeCurrentSession_MissingActiveToken_DoesNothing() {
        // Given
        String refreshToken = "unknown-token";
        String tokenHash = hashToken(refreshToken);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("auth:refresh:active:" + tokenHash)).willReturn(null);

        // When
        refreshTokenSessionService.revokeCurrentSession(refreshToken);

        // Then
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void rotateSession_NullToken_ThrowsAppException() {
        // Given
        String refreshToken = null;

        // When
        // Then
        assertThatThrownBy(() -> refreshTokenSessionService.rotateSession(refreshToken, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void rotateSession_BlankToken_ThrowsAppException() {
        // Given
        String refreshToken = "   ";

        // When
        // Then
        assertThatThrownBy(() -> refreshTokenSessionService.rotateSession(refreshToken, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void revokeCurrentSession_NullToken_DoesNothing() {
        // When
        refreshTokenSessionService.revokeCurrentSession(null);

        // Then
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void revokeCurrentSession_BlankToken_DoesNothing() {
        // When
        refreshTokenSessionService.revokeCurrentSession("");

        // Then
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void rotateSession_AcquireLockFails_ThrowsAppException() {
        // Given
        String refreshToken = "some-token";
        String tokenHash = hashToken(refreshToken);
        UUID sessionId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("auth:refresh:active:" + tokenHash)).willReturn(sessionId.toString());
        given(valueOperations.setIfAbsent(eq("auth:session-lock:" + sessionId), anyString(), any(Duration.class))).willReturn(false);

        // When
        // Then
        assertThatThrownBy(() -> refreshTokenSessionService.rotateSession(refreshToken, null))
                .isInstanceOf(AppException.class)
                .hasMessage("Refresh token rotation is already in progress")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private RefreshSessionData createActiveSessionData(UUID sessionId, UUID userId, String tokenHash) {
        long now = Instant.now().toEpochMilli();
        return new RefreshSessionData(
                sessionId,
                userId,
                tokenHash,
                now,
                now + 600_000,
                now,
                "127.0.0.1",
                "JUnit",
                "ACTIVE",
                null
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
