-- V6: Upgrade Content module tables & AI usage logs (Merged & Optimized)
-- Squashed V7 into V6 for a cleaner local development history.

-- 1. Ensure status is VARCHAR(50) as per latest Phase 5 design (Reverting Enum attempt)
-- If status was already Enum from a previous failed run, this will cast back to TEXT carefully.
ALTER TABLE contents ALTER COLUMN status DROP DEFAULT;
ALTER TABLE contents 
    ALTER COLUMN status TYPE VARCHAR(50) USING status::text,
    ALTER COLUMN status SET DEFAULT 'DRAFT';

-- 2. Performance indexes (IDOR safety & sorting)
CREATE INDEX IF NOT EXISTS idx_contents_campaign_id ON contents(campaign_id);
CREATE INDEX IF NOT EXISTS idx_contents_user_id     ON contents(user_id);
CREATE INDEX IF NOT EXISTS idx_contents_status      ON contents(status);
CREATE INDEX IF NOT EXISTS idx_contents_deleted_at  ON contents(deleted_at) WHERE deleted_at IS NULL;

-- 3. Upgrade ai_usage_logs: link to content and add total token count
ALTER TABLE ai_usage_logs
    ADD COLUMN IF NOT EXISTS content_id   UUID REFERENCES contents(id),
    ADD COLUMN IF NOT EXISTS total_tokens INT  DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ai_logs_content_id ON ai_usage_logs(content_id);
