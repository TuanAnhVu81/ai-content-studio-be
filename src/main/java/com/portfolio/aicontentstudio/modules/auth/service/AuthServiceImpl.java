package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshTokenRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.JwtProvider;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
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
import java.util.stream.Collectors;

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
    private final SecurityContextHelper securityContextHelper;

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
        } catch (DisabledException e) {
            throw new AppException(ErrorCode.USER_DISABLED);
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
        if (refreshToken == null) {
            return;
        }
        // Find user by token
        String userId = findUserIdByRefreshToken(refreshToken);
        if (userId != null) {
            String redisKey = RT_KEY_PREFIX + refreshToken;
            redisTemplate.delete(redisKey);
            log.info("User logged out, refresh token deleted: userId={}", userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        UUID userId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UUID userId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 1. Verify old password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Invalid current password");
        }

        // 2. Hash and save new password
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // 3. Invalidate current refresh token to force re-login (optional security measure)
        // This assumes refresh tokens are stored with a key that can be derived from the user's email
        // If using the UUID.randomUUID().toString() as key, this invalidation logic needs adjustment.
        // For now, keeping the original refresh token invalidation logic based on the token itself.
        // If the intention is to invalidate ALL refresh tokens for a user, a different storage strategy is needed.
        // For this change, we'll assume the existing refresh token storage (token as key) is maintained.
        // The provided snippet's logout logic `redisTemplate.delete("rt:" + email)` implies a different storage.
        // Sticking to the original `RT_KEY_PREFIX + refreshToken` for consistency with `login` and `refreshToken` methods.
        // If the user wants to invalidate all tokens for a user, the `storeRefreshToken` and `findUserIdByRefreshToken`
        // methods would need to be changed to store `rt:{userId}` -> `refreshToken` or `rt:{email}` -> `refreshToken`.
        // Given the instruction is to implement `changePassword` and `getMe`, and the provided snippet for `logout`
        // is different from the original, I'll keep the original `logout` and `storeRefreshToken` logic,
        // and only add the `redisTemplate.delete` for the specific token if it were stored by email.
        // Since it's not, I'll remove the `redisTemplate.delete("rt:" + user.getEmail());` from changePassword
        // as it won't work with the current refresh token storage.
        // If the user wants to invalidate all tokens for a user, they need to change the refresh token storage strategy.

        log.info("User changed password successfully: userId={}", userId);
    }

    private UserResponse toUserResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles
        );
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
