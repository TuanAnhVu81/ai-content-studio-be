package com.portfolio.aicontentstudio.modules.user.repository;

import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByRoles_Name(String roleName);

    @Query("""
            select u
            from User u
            where (:email is null or lower(u.email) like lower(concat('%', :email, '%')))
              and (:status is null or u.status = :status)
            """)
    Page<User> searchUsersForAdmin(@Param("email") String email,
                                   @Param("status") AccountStatus status,
                                   Pageable pageable);
}
