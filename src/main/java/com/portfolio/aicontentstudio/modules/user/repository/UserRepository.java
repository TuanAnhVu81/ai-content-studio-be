package com.portfolio.aicontentstudio.modules.user.repository;

import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u where u.email = :email")
    Optional<User> findWithRolesByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") UUID id);

    Page<User> findAllByStatus(AccountStatus status, Pageable pageable);

    @Query("""
            select u
            from User u
            where lower(u.email) like concat('%', lower(:email), '%')
            """)
    Page<User> searchUsersByEmailForAdmin(@Param("email") String email, Pageable pageable);

    @Query("""
            select u
            from User u
            where lower(u.email) like concat('%', lower(:email), '%')
              and u.status = :status
            """)
    Page<User> searchUsersByEmailAndStatusForAdmin(@Param("email") String email,
                                                   @Param("status") AccountStatus status,
                                                   Pageable pageable);
}
