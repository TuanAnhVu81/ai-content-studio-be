package com.portfolio.aicontentstudio.modules.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.aicontentstudio.config.properties.AuthSessionProperties;
import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionData;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenSessionServiceImpl implements RefreshTokenSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String REVOKED_STATUS = "REVOKED";

    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String ACTIVE_TOKEN_KEY_PREFIX = "auth:refresh:active:";
    private static final String USED_TOKEN_KEY_PREFIX = "auth:refresh:used:";
    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user-sessions:";
    private static final String SESSION_LOCK_KEY_PREFIX = "auth:session-lock:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthSessionProperties authSessionProperties;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Override
    @Transactional
    public RefreshSessionResult createSession(UUID userId, ClientMetadata clientMetadata) {
        String refreshToken = generateRefreshToken();
        String tokenHash = hashToken(refreshToken);
        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = now.plusMillis(refreshTokenExpirationMs);

        RefreshSessionData sessionData = new RefreshSessionData(
                sessionId,
                userId,
                tokenHash,
                now.toEpochMilli(),
                expiresAt.toEpochMilli(),
                now.toEpochMilli(),
                sanitize(clientMetadata != null ? clientMetadata.ipAddress() : null),
                sanitize(clientMetadata != null ? clientMetadata.userAgent() : null),
                ACTIVE_STATUS,
                null
        );

        saveActiveSession(sessionData, Duration.ofMillis(refreshTokenExpirationMs));
        enforceSessionLimit(userId);

        return new RefreshSessionResult(sessionId, userId, refreshToken);
    }

    @Override
    @Transactional
    public RefreshSessionResult rotateSession(String refreshToken, ClientMetadata clientMetadata) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = hashToken(refreshToken);
        String sessionIdValue = redisTemplate.opsForValue().get(activeTokenKey(tokenHash));

        if (sessionIdValue == null) {
            handleMissingActiveToken(tokenHash);
        }

        UUID sessionId = UUID.fromString(sessionIdValue);
        String lockOwner = UUID.randomUUID().toString();

        // Serialize refresh operations per session to avoid race conditions during rotation.
        if (!acquireSessionLock(sessionId, lockOwner)) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token rotation is already in progress");
        }

        try {
            String activeSessionIdValue = redisTemplate.opsForValue().get(activeTokenKey(tokenHash));
            if (activeSessionIdValue == null || !sessionId.toString().equals(activeSessionIdValue)) {
                handleMissingActiveToken(tokenHash);
            }

            RefreshSessionData sessionData = getSession(sessionId)
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

            validateActiveSession(sessionData, tokenHash);

            String newRefreshToken = generateRefreshToken();
            String newTokenHash = hashToken(newRefreshToken);
            Instant now = Instant.now();
            Duration remainingTtl = remainingTtl(sessionData.expiresAtEpochMs(), now);

            redisTemplate.delete(activeTokenKey(tokenHash));
            // Keep the previous hash for the remaining TTL so reuse attempts can trigger full-session revocation.
            redisTemplate.opsForValue().set(usedTokenKey(tokenHash), sessionId.toString(), remainingTtl);

            RefreshSessionData rotatedSession = new RefreshSessionData(
                    sessionData.sessionId(),
                    sessionData.userId(),
                    newTokenHash,
                    sessionData.createdAtEpochMs(),
                    sessionData.expiresAtEpochMs(),
                    now.toEpochMilli(),
                    sanitize(clientMetadata != null ? clientMetadata.ipAddress() : null),
                    sanitize(clientMetadata != null ? clientMetadata.userAgent() : null),
                    ACTIVE_STATUS,
                    null
            );

            saveActiveSession(rotatedSession, remainingTtl);
            return new RefreshSessionResult(sessionId, sessionData.userId(), newRefreshToken);
        } finally {
            releaseSessionLock(sessionId, lockOwner);
        }
    }

    @Override
    @Transactional
    public void revokeCurrentSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(refreshToken);
        String sessionIdValue = redisTemplate.opsForValue().get(activeTokenKey(tokenHash));

        if (sessionIdValue == null) {
            return;
        }

        revokeSession(UUID.fromString(sessionIdValue));
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId) {
        String userSessionsKey = userSessionsKey(userId);
        var sessionMembers = redisTemplate.opsForZSet().range(userSessionsKey, 0, -1);
        if (sessionMembers == null || sessionMembers.isEmpty()) {
            redisTemplate.delete(userSessionsKey);
            return;
        }

        List<String> activeSessionIds = sessionMembers.stream().toList();

        for (String sessionIdValue : activeSessionIds) {
            revokeSession(UUID.fromString(sessionIdValue));
        }

        redisTemplate.delete(userSessionsKey);
        log.info("Revoked all refresh sessions for userId={}", userId);
    }

    private void handleMissingActiveToken(String tokenHash) {
        String reusedSessionIdValue = redisTemplate.opsForValue().get(usedTokenKey(tokenHash));
        if (reusedSessionIdValue != null) {
            UUID reusedSessionId = UUID.fromString(reusedSessionIdValue);
            getSession(reusedSessionId).ifPresent(session -> revokeAllSessions(session.userId()));
            throw new AppException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }
        throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private void validateActiveSession(RefreshSessionData sessionData, String expectedTokenHash) {
        if (!ACTIVE_STATUS.equals(sessionData.status())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!expectedTokenHash.equals(sessionData.currentTokenHash())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (sessionData.expiresAtEpochMs() <= Instant.now().toEpochMilli()) {
            revokeSession(sessionData.sessionId());
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void enforceSessionLimit(UUID userId) {
        String userSessionsKey = userSessionsKey(userId);
        Long sessionCount = redisTemplate.opsForZSet().zCard(userSessionsKey);

        if (sessionCount == null || sessionCount <= authSessionProperties.getMaxActiveSessionsPerUser()) {
            return;
        }

        long overflow = sessionCount - authSessionProperties.getMaxActiveSessionsPerUser();
        var sessionMembers = redisTemplate.opsForZSet().range(userSessionsKey, 0, overflow - 1);
        if (sessionMembers == null || sessionMembers.isEmpty()) {
            return;
        }

        List<String> oldestSessionIds = sessionMembers.stream().toList();

        for (String sessionIdValue : oldestSessionIds) {
            revokeSession(UUID.fromString(sessionIdValue));
        }
    }

    private void revokeSession(UUID sessionId) {
        Optional<RefreshSessionData> sessionOptional = getSession(sessionId);
        if (sessionOptional.isEmpty()) {
            return;
        }

        RefreshSessionData sessionData = sessionOptional.get();
        Instant now = Instant.now();
        Duration remainingTtl = remainingTtl(sessionData.expiresAtEpochMs(), now);

        redisTemplate.delete(activeTokenKey(sessionData.currentTokenHash()));
        // A revoked token should never become valid again; keep the hash in the reuse bucket until natural expiry.
        redisTemplate.opsForValue().set(usedTokenKey(sessionData.currentTokenHash()), sessionId.toString(), remainingTtl);
        redisTemplate.opsForZSet().remove(userSessionsKey(sessionData.userId()), sessionId.toString());

        RefreshSessionData revokedSession = new RefreshSessionData(
                sessionData.sessionId(),
                sessionData.userId(),
                sessionData.currentTokenHash(),
                sessionData.createdAtEpochMs(),
                sessionData.expiresAtEpochMs(),
                sessionData.lastRotatedAtEpochMs(),
                sessionData.ipAddress(),
                sessionData.userAgent(),
                REVOKED_STATUS,
                now.toEpochMilli()
        );

        saveSessionRecord(revokedSession, remainingTtl);
    }

    private void saveActiveSession(RefreshSessionData sessionData, Duration ttl) {
        saveSessionRecord(sessionData, ttl);
        redisTemplate.opsForValue().set(activeTokenKey(sessionData.currentTokenHash()), sessionData.sessionId().toString(), ttl);
        redisTemplate.opsForZSet().add(userSessionsKey(sessionData.userId()), sessionData.sessionId().toString(), sessionData.createdAtEpochMs());
        redisTemplate.expire(userSessionsKey(sessionData.userId()), ttl);
    }

    private void saveSessionRecord(RefreshSessionData sessionData, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(sessionKey(sessionData.sessionId()), objectMapper.writeValueAsString(sessionData), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize refresh session metadata", ex);
        }
    }

    private Optional<RefreshSessionData> getSession(UUID sessionId) {
        String json = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, RefreshSessionData.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize refresh session metadata", ex);
        }
    }

    private boolean acquireSessionLock(UUID sessionId, String lockOwner) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                sessionLockKey(sessionId),
                lockOwner,
                Duration.ofSeconds(authSessionProperties.getLockTimeoutSeconds())
        );
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseSessionLock(UUID sessionId, String lockOwner) {
        String lockKey = sessionLockKey(sessionId);
        String currentLockOwner = redisTemplate.opsForValue().get(lockKey);
        if (lockOwner.equals(currentLockOwner)) {
            redisTemplate.delete(lockKey);
        }
    }

    private Duration remainingTtl(long expiresAtEpochMs, Instant now) {
        return Duration.ofMillis(Math.max(1, expiresAtEpochMs - now.toEpochMilli()));
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String sanitize(String value) {
        return value == null ? null : value.trim();
    }

    private String sessionKey(UUID sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String activeTokenKey(String tokenHash) {
        return ACTIVE_TOKEN_KEY_PREFIX + tokenHash;
    }

    private String usedTokenKey(String tokenHash) {
        return USED_TOKEN_KEY_PREFIX + tokenHash;
    }

    private String userSessionsKey(UUID userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }

    private String sessionLockKey(UUID sessionId) {
        return SESSION_LOCK_KEY_PREFIX + sessionId;
    }
}
