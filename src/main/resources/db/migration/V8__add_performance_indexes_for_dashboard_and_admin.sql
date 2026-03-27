-- V8: Add production-oriented indexes for content dashboard, admin search, and AI usage analytics

-- 1. Speed up paginated content lists by campaign/user and recent-content lookups
CREATE INDEX IF NOT EXISTS idx_contents_campaign_user_created_active
    ON contents(campaign_id, user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_contents_user_created_active
    ON contents(user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- 2. Speed up AI usage dashboard/admin aggregate queries
CREATE INDEX IF NOT EXISTS idx_ai_logs_created_at
    ON ai_usage_logs(created_at);

CREATE INDEX IF NOT EXISTS idx_ai_logs_user_created_at
    ON ai_usage_logs(user_id, created_at DESC);

-- 3. Support case-insensitive admin email search
CREATE INDEX IF NOT EXISTS idx_users_email_lower
    ON users(lower(email));
