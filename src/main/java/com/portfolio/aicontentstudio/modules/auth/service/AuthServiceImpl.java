package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshTokenRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of authentication business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // Redis key prefix for refresh tokens (Mapping: rt:{token} -> userId)
    private static final String RT_KEY_PREFIX = "rt:";

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // Check for duplicate email before registering
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Find the default ROLE_USER to assign
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User newUser = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .roles(Set.of(defaultRole))
                .build();

        userRepository.save(newUser);
        log.info("New user registered with email: {}", request.email());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            // Delegate credential verification to Spring Security's AuthenticationManager
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            // Specifically catch wrong email/password and throw custom business error
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());

        // Generate a new Access Token
        String accessToken = jwtProvider.generateAccessToken(userDetails);

        // Generate and store Refresh Token in Redis (O(1) lookup)
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        String refreshToken = UUID.randomUUID().toString();
        storeRefreshToken(refreshToken, user.getId().toString());

        return new AuthResponse(accessToken, refreshToken);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.refreshToken();

        // O(1) lookup: Get userId directly using the token as key from Redis
        String userId = findUserIdByRefreshToken(incomingToken);

        if (userId == null) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Optional: Implement token rotation by deleting old token and creating new/recycling old one
        // For now, we reuse the same userId and provided a fresh access token
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtProvider.generateAccessToken(userDetails);

        return new AuthResponse(newAccessToken, incomingToken);
    }

    @Override
    public void logout(String refreshToken) {
        // Invalidate the specific refresh token provided by the client
        if (refreshToken != null) {
            redisTemplate.delete(RT_KEY_PREFIX + refreshToken);
            log.info("Refresh token invalidated successfully");
        }
    }

    // Store the refresh token in Redis with token as KEY (O(1) optimization)
    private void storeRefreshToken(String refreshToken, String userId) {
        String redisKey = RT_KEY_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(redisKey, userId, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    // Direct lookup from Redis
    private String findUserIdByRefreshToken(String token) {
        return redisTemplate.opsForValue().get(RT_KEY_PREFIX + token);
    }
}
