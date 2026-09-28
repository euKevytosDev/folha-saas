CREATE TABLE commercial_leads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    business_name   VARCHAR(160) NOT NULL,
    brief           VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commercial_leads_created_at ON commercial_leads (created_at DESC);
