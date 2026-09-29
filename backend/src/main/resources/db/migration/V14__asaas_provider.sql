ALTER TABLE establishment_payment_settings
    DROP CONSTRAINT ck_payment_settings_provider;

ALTER TABLE establishment_payment_settings
    ADD CONSTRAINT ck_payment_settings_provider
        CHECK (provider IN ('MERCADO_PAGO', 'ASAAS', 'MANUAL', 'MOCK'));

ALTER TABLE establishment_payment_settings
    ADD COLUMN sandbox BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_provider;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_provider
        CHECK (provider IN ('MERCADO_PAGO', 'ASAAS', 'MANUAL', 'MOCK'));
