package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.auth.dto.AuthSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.ChangePasswordRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.ClientMetadata;
import com.portfolio.aicontentstudio.modules.auth.dto.LoginRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.RefreshSessionResult;
import com.portfolio.aicontentstudio.modules.auth.dto.RegisterRequest;
import com.portfolio.aicontentstudio.modules.auth.dto.UserResponse;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.JwtProvider;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

/**
 * Authentication business logic backed by stateless access JWTs and Redis refresh sessions.
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
    private final UserDetailsService userDetailsService;
    private final SecurityContextHelper securityContextHelper;
    private final RefreshTokenSessionService refreshTokenSessionService;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User newUser = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .roles(Set.of(defaultRole))
                .build();

        userRepository.save(newUser);
        log.info("New user registered with email={}", request.email());
    }

    @Override
    public AuthSessionResult login(LoginRequest request, ClientMetadata clientMetadata) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException ex) {
            throw new AppException(ErrorCode.USER_DISABLED);
        } catch (AuthenticationException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findWithRolesByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());

        String accessToken = jwtProvider.generateAccessToken(userDetails);
        RefreshSessionResult refreshSessionResult = refreshTokenSessionService.createSession(user.getId(), clientMetadata);

        return new AuthSessionResult(accessToken, refreshSessionResult.refreshToken(), toUserResponse(user));
    }

    @Override
    public AuthSessionResult refreshToken(String refreshToken, ClientMetadata clientMetadata) {
        RefreshSessionResult refreshSessionResult = refreshTokenSessionService.rotateSession(refreshToken, clientMetadata);

        User user = userRepository.findWithRolesById(refreshSessionResult.userId())
                .orElseThrow(() -> {
                    refreshTokenSessionService.revokeAllSessions(refreshSessionResult.userId());
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });

        if (user.getStatus() != AccountStatus.ACTIVE) {
            refreshTokenSessionService.revokeAllSessions(user.getId());
            throw new AppException(ErrorCode.USER_DISABLED);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtProvider.generateAccessToken(userDetails);

        return new AuthSessionResult(accessToken, refreshSessionResult.refreshToken(), toUserResponse(user));
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenSessionService.revokeCurrentSession(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        UUID userId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UUID userId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Invalid current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenSessionService.revokeAllSessions(userId);

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
}
