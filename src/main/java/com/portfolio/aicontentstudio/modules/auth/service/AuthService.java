package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.modules.auth.dto.AuthSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;

/**
 * Contract for authentication operations.
 */
public interface AuthService {

    void register(RegisterRequest request);

    AuthSessionResult login(LoginRequest request, ClientMetadata clientMetadata);

    AuthSessionResult refreshToken(String refreshToken, ClientMetadata clientMetadata);

    void logout(String refreshToken);
    
    UserResponse getMe();
    
    void changePassword(ChangePasswordRequest request);
}
