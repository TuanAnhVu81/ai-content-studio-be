package com.portfolio.aicontentstudio.security;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper to access the current authenticated principal conveniently.
 * Use this instead of repeating SecurityContextHolder logic across services.
 */
@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final UserRepository userRepository;

    /**
     * Returns the email (username) of the currently authenticated user.
     */
    public String getCurrentUserEmail() {
        return getPrincipal().getUsername();
    }

    /**
     * Returns the UUID of the currently authenticated user.
     * Loads from the database using the email from the JWT claim.
     */
    public UUID getCurrentUserId() {
        String email = getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }

    // Get the UserDetails principal from SecurityContext
    private UserDetails getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found in SecurityContext");
        }
        return (UserDetails) authentication.getPrincipal();
    }
}
