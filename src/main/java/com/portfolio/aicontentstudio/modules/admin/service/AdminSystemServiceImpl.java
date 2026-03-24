package com.portfolio.aicontentstudio.modules.admin.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminCampaignResponse;
import com.portfolio.aicontentstudio.modules.admin.dto.AdminRecentContentResponse;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import com.portfolio.aicontentstudio.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSystemServiceImpl implements AdminSystemService {

    private static final String ACTION_HARD_DELETE_CONTENT = "HARD_DELETE_CONTENT";
    private static final int CONTENT_PREVIEW_LIMIT = 160;

    private final CampaignRepository campaignRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCampaignResponse> getAllCampaigns(Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findAll(pageable);
        Map<UUID, String> userEmails = resolveUserEmails(campaigns.getContent());

        return campaigns.map(campaign -> new AdminCampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getMetadata(),
                campaign.getUserId(),
                userEmails.getOrDefault(campaign.getUserId(), "unknown"),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRecentContentResponse> getRecentContents() {
        return contentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toRecentContentResponse)
                .toList();
    }

    @Override
    @Transactional
    public void hardDeleteContent(UUID contentId, String reason) {
        UUID adminId = securityContextHelper.getCurrentUserId();
        Content content = contentRepository.findWithCampaignAndUserById(contentId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTENT_NOT_FOUND));

        contentRepository.hardDeleteById(contentId);
        adminAuditLogService.logAction(adminId, ACTION_HARD_DELETE_CONTENT, content.getId(), reason);

        log.info("Admin hard-deleted content: adminId={}, contentId={}", adminId, contentId);
    }

    private AdminRecentContentResponse toRecentContentResponse(Content content) {
        return new AdminRecentContentResponse(
                content.getId(),
                content.getCampaign().getId(),
                content.getCampaign().getName(),
                content.getUser().getId(),
                content.getUser().getEmail(),
                content.getTargetKeyword(),
                buildPreview(content.getGeneratedText()),
                content.getBannerUrl(),
                content.getStatus(),
                content.getCreatedAt()
        );
    }

    private Map<UUID, String> resolveUserEmails(List<Campaign> campaigns) {
        Map<UUID, String> userEmails = new HashMap<>();
        List<UUID> userIds = campaigns.stream()
                .map(Campaign::getUserId)
                .distinct()
                .toList();

        for (User user : userRepository.findAllById(userIds)) {
            userEmails.put(user.getId(), user.getEmail());
        }

        return userEmails;
    }

    private String buildPreview(String generatedText) {
        if (generatedText == null || generatedText.isBlank()) {
            return "";
        }

        String trimmed = generatedText.trim();
        if (trimmed.length() <= CONTENT_PREVIEW_LIMIT) {
            return trimmed;
        }

        return trimmed.substring(0, CONTENT_PREVIEW_LIMIT) + "...";
    }
}
