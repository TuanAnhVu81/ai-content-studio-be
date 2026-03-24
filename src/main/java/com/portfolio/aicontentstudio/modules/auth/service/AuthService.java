package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.modules.auth.dto.AuthResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshTokenRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;

/**
 * Contract for authentication operations.
 */
public interface AuthService {

    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);
    
    UserResponse getMe();
    
    void changePassword(ChangePasswordRequest request);
}
