package com.portfolio.aicontentstudio.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Helper to access the current authenticated principal conveniently.
 * Use this instead of repeating SecurityContextHolder logic across services.
 */
@Component
public class SecurityContextHelper {

    /**
     * Returns the email (username) of the currently authenticated user.
     */
    public String getCurrentUserEmail() {
        return getPrincipal().getUsername();
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
