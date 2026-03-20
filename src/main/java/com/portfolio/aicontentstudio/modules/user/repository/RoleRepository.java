package com.portfolio.aicontentstudio.modules.user.repository;

import com.portfolio.aicontentstudio.modules.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Role entity.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);
}
