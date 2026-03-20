package com.portfolio.aicontentstudio.modules.user.repository;

import com.portfolio.aicontentstudio.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
