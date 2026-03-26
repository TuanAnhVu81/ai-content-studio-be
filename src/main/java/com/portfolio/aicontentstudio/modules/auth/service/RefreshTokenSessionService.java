package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionResult;

import java.util.UUID;

public interface RefreshTokenSessionService {

    RefreshSessionResult createSession(UUID userId, ClientMetadata clientMetadata);

    RefreshSessionResult rotateSession(String refreshToken, ClientMetadata clientMetadata);

    void revokeCurrentSession(String refreshToken);

    void revokeAllSessions(UUID userId);
}
