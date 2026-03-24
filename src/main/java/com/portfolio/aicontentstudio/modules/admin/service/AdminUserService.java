package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.modules.admin.dto.AdminUserResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.UpdateUserStatusRequest;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {

    Page<AdminUserResponse> getUsers(String email, AccountStatus status, Pageable pageable);

    AdminUserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request);
}
