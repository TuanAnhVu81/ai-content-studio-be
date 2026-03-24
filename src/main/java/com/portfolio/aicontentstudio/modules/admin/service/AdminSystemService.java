package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.modules.admin.dto.AdminCampaignResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminRecentContentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminSystemService {

    Page<AdminCampaignResponse> getAllCampaigns(Pageable pageable);

    List<AdminRecentContentResponse> getRecentContents();

    void hardDeleteContent(UUID contentId, String reason);
}
