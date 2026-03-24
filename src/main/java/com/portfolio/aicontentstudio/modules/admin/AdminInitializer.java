package com.portfolio.aicontentstudio.modules.admin;

import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.Role;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.RoleRepository;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Production-ready initializer to ensure at least one Admin exists at startup.
 * Values are drawn from environment variables for security.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.initial-email:admin@aicontentstudio.com}")
    private String adminEmail;

    @Value("${app.admin.initial-password:AdminPass@123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking for system administrator accounts...");

        // Strategy: We check if ANY user has ROLE_ADMIN. 
        // This is more flexible than just checking for a specific email.
        boolean adminExists = userRepository.existsByRoles_Name("ROLE_ADMIN");

        if (!adminExists) {
            log.warn("No administrator found! Initializing default admin account: {}", adminEmail);
            initializeAdmin();
        } else {
            log.info("Administrator account verified. System is ready.");
        }
    }

    private void initializeAdmin() {
        // 1. Find the ROLE_ADMIN (Should be seeded by Flyway V4)
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("FATAL: ROLE_ADMIN not found in database. Check Flyway V4."));

        // 2. Create the User entity
        User adminUser = User.builder()
                .email(adminEmail)
                .fullName("System Administrator")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build();

        // 3. Save to DB
        userRepository.save(adminUser);
        log.info("Default admin account created successfully. PLEASE CHANGE PASSWORD AFTER FIRST LOGIN!");
    }
}
