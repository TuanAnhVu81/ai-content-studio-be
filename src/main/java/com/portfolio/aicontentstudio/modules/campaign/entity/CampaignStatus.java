package com.portfolio.aicontentstudio.modules.campaign.entity;

/**
 * Life-cycle statuses for a Campaign.
 * DRAFT   -> created but not actively used yet.
 * ACTIVE  -> currently in use.
 * ARCHIVED-> hidden from main view but preserved for history.
 */
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
