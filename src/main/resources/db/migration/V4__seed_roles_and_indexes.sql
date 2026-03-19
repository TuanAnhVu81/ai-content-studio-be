-- V4: Seed initial roles and create performance indexes
INSERT INTO roles (name, description) VALUES 
('ROLE_USER', 'Standard user with basic features'),
('ROLE_ADMIN', 'Administrator with full system control')
ON CONFLICT (name) DO NOTHING;

-- Indexes for performance (JOINs and filters)
CREATE INDEX IF NOT EXISTS idx_contents_campaign_id ON contents (campaign_id);
CREATE INDEX IF NOT EXISTS idx_contents_user_id ON contents (user_id);
CREATE INDEX IF NOT EXISTS idx_contents_deleted_at ON contents (deleted_at);
CREATE INDEX IF NOT EXISTS idx_campaigns_user_id ON campaigns (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_user_id ON ai_usage_logs (user_id);
