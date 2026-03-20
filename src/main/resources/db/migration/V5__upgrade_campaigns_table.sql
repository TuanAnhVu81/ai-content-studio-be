-- V5: Upgrade campaigns table to Production-Ready schema
-- Add: status enum, metadata JSONB, deleted_at (soft delete), unique constraint

-- Add campaign status type (DRAFT, ACTIVE, ARCHIVED)
DO $$ BEGIN
    CREATE TYPE campaign_status AS ENUM ('DRAFT', 'ACTIVE', 'ARCHIVED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Add new columns to existing campaigns table
ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS status     campaign_status NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS metadata   JSONB,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

-- Remove old columns that have been replaced by JSONB metadata
ALTER TABLE campaigns
    DROP COLUMN IF EXISTS objective,
    DROP COLUMN IF EXISTS target_audience;

-- Enforce unique campaign name per user (prevents duplicate names for same user)
ALTER TABLE campaigns
    DROP CONSTRAINT IF EXISTS uq_campaign_name_user;

ALTER TABLE campaigns
    ADD CONSTRAINT uq_campaign_name_user UNIQUE (name, user_id);

-- Index to speed up common queries (get all campaigns by user)
CREATE INDEX IF NOT EXISTS idx_campaigns_user_id ON campaigns(user_id);

-- Partial index to skip soft-deleted records efficiently
CREATE INDEX IF NOT EXISTS idx_campaigns_active ON campaigns(user_id, status) WHERE deleted_at IS NULL;
