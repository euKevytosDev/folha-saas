-- Folha FASE 5: pagamentos, webhooks e idempotência.

CREATE TABLE establishment_payment_settings (
    establishment_id   UUID PRIMARY KEY REFERENCES establishments (id) ON DELETE CASCADE,
    provider           VARCHAR(32) NOT NULL DEFAULT 'MERCADO_PAGO',
    access_token       TEXT,
    webhook_secret     VARCHAR(255),
    pix_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    online_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    mock_mode          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payment_settings_provider CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'MOCK'))
);

CREATE TRIGGER trg_establishment_payment_settings_updated_at
    BEFORE UPDATE ON establishment_payment_settings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id     UUID NOT NULL REFERENCES establishments (id),
    order_id             UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    provider             VARCHAR(32) NOT NULL,
    status               VARCHAR(32) NOT NULL,
    method               VARCHAR(24) NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             CHAR(3) NOT NULL DEFAULT 'BRL',
    external_id          VARCHAR(120),
    idempotency_key      VARCHAR(120),
    pix_copy_paste       TEXT,
    pix_qr_code_base64   TEXT,
    checkout_url         VARCHAR(500),
    raw_response         TEXT,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_external UNIQUE (provider, external_id),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'MOCK')),
    CONSTRAINT ck_payments_status CHECK (status IN (
        'PENDING', 'AUTHORIZED', 'PAID', 'FAILED', 'REFUNDED', 'CANCELLED', 'EXPIRED'
    )),
    CONSTRAINT ck_payments_method CHECK (method IN ('PIX', 'CASH', 'CARD', 'ON_DELIVERY')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payments_establishment_id ON payments (establishment_id);
CREATE INDEX idx_payments_establishment_status ON payments (establishment_id, status);
CREATE INDEX idx_payments_idempotency ON payments (establishment_id, idempotency_key);

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE payment_webhook_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID REFERENCES establishments (id),
    provider           VARCHAR(32) NOT NULL,
    provider_event_id  VARCHAR(160) NOT NULL,
    payment_id         UUID REFERENCES payments (id),
    payload            TEXT NOT NULL,
    processed          BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_payment_webhook_events UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_payment_webhook_events_payment ON payment_webhook_events (payment_id);

CREATE TABLE idempotency_keys (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID NOT NULL REFERENCES establishments (id),
    scope              VARCHAR(64) NOT NULL,
    key_hash           VARCHAR(64) NOT NULL,
    request_hash       VARCHAR(64) NOT NULL,
    response_body      TEXT,
    resource_id        UUID,
    http_status        INT NOT NULL DEFAULT 201,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_idempotency_keys UNIQUE (establishment_id, scope, key_hash)
);

CREATE INDEX idx_idempotency_keys_expires ON idempotency_keys (expires_at);
