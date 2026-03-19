-- V3: Create usage logs for AI token tracking (Refined to match DB.md)
CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Strict logging
    content_id UUID REFERENCES contents(id) ON DELETE SET NULL,
    prompt_tokens INT DEFAULT 0,
    response_tokens INT DEFAULT 0,
    model_name VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
