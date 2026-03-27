package com.portfolio.aicontentstudio.modules.content.repository;

import java.util.UUID;

public interface CampaignContentCountView {

    UUID getCampaignId();

    long getContentCount();
}
