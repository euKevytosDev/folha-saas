-- Folha: integração Focus NFe (NFC-e) por estabelecimento.

CREATE TABLE establishment_fiscal_settings (
    establishment_id              UUID PRIMARY KEY REFERENCES establishments (id) ON DELETE CASCADE,
    enabled                       BOOLEAN NOT NULL DEFAULT FALSE,
    environment                   VARCHAR(16) NOT NULL DEFAULT 'HOMOLOG',
    api_token                     TEXT,
    cnpj                          VARCHAR(14),
    auto_emit_on_paid             BOOLEAN NOT NULL DEFAULT TRUE,
    default_ncm                   VARCHAR(8) NOT NULL DEFAULT '21069090',
    default_cfop                  VARCHAR(4) NOT NULL DEFAULT '5102',
    icms_origem                   SMALLINT NOT NULL DEFAULT 0,
    icms_situacao_tributaria      VARCHAR(4) NOT NULL DEFAULT '102',
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_fiscal_environment CHECK (environment IN ('HOMOLOG', 'PRODUCTION'))
);

CREATE TRIGGER trg_establishment_fiscal_settings_updated_at
    BEFORE UPDATE ON establishment_fiscal_settings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE orders
    ADD COLUMN nfce_ref            VARCHAR(80),
    ADD COLUMN nfce_status         VARCHAR(24) NOT NULL DEFAULT 'NONE',
    ADD COLUMN nfce_number         VARCHAR(20),
    ADD COLUMN nfce_series         VARCHAR(10),
    ADD COLUMN nfce_chave          VARCHAR(50),
    ADD COLUMN nfce_url_danfe      VARCHAR(500),
    ADD COLUMN nfce_url_xml        VARCHAR(500),
    ADD COLUMN nfce_qrcode_url     VARCHAR(500),
    ADD COLUMN nfce_error_message  TEXT,
    ADD COLUMN nfce_emitted_at     TIMESTAMPTZ;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_nfce_status CHECK (nfce_status IN (
        'NONE', 'PROCESSING', 'AUTHORIZED', 'DENIED', 'CANCELLED', 'ERROR'
    ));

CREATE UNIQUE INDEX uk_orders_nfce_ref
    ON orders (establishment_id, nfce_ref)
    WHERE nfce_ref IS NOT NULL;
