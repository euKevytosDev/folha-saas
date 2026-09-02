-- Folha FASE 1: fundação do schema multi-tenant.
-- Tabelas de catálogo, pedidos e pagamentos entram nas fases seguintes.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE establishments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(160) NOT NULL,
    slug            VARCHAR(120) NOT NULL,
    logo_url        VARCHAR(500),
    description     TEXT,
    phone           VARCHAR(32),
    email           VARCHAR(255),
    address         VARCHAR(255),
    city            VARCHAR(120),
    state           CHAR(2),
    zip_code        VARCHAR(16),
    opening_hours   JSONB,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    plan_code       VARCHAR(32) NOT NULL DEFAULT 'BASIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_establishments_slug UNIQUE (slug),
    CONSTRAINT ck_establishments_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_establishments_plan CHECK (plan_code IN ('BASIC', 'PRO', 'PREMIUM'))
);

CREATE INDEX idx_establishments_active ON establishments (active);

CREATE TRIGGER trg_establishments_updated_at
    BEFORE UPDATE ON establishments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID REFERENCES establishments (id),
    name               VARCHAR(160) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(32) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('SUPER_ADMIN', 'OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT ck_users_tenant CHECK (
        (role = 'SUPER_ADMIN' AND establishment_id IS NULL)
        OR
        (role <> 'SUPER_ADMIN' AND establishment_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email));
CREATE INDEX idx_users_establishment_id ON users (establishment_id);
CREATE INDEX idx_users_establishment_role ON users (establishment_id, role);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
