-- Folha FASE 6: entrega (frete fixo), cupons e movimentação de estoque.

CREATE TABLE establishment_delivery_settings (
    establishment_id      UUID PRIMARY KEY REFERENCES establishments (id) ON DELETE CASCADE,
    delivery_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    pickup_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    fixed_fee             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    free_above_amount     NUMERIC(12, 2),
    estimated_minutes     INT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_delivery_fixed_fee CHECK (fixed_fee >= 0),
    CONSTRAINT ck_delivery_free_above CHECK (free_above_amount IS NULL OR free_above_amount >= 0),
    CONSTRAINT ck_delivery_eta CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0)
);

CREATE TRIGGER trg_establishment_delivery_settings_updated_at
    BEFORE UPDATE ON establishment_delivery_settings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE coupons (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id      UUID NOT NULL REFERENCES establishments (id),
    code                  VARCHAR(40) NOT NULL,
    description           VARCHAR(255),
    discount_type         VARCHAR(16) NOT NULL,
    discount_value        NUMERIC(12, 2) NOT NULL,
    min_order_amount      NUMERIC(12, 2),
    max_discount_amount   NUMERIC(12, 2),
    usage_limit           INT,
    used_count            INT NOT NULL DEFAULT 0,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at             TIMESTAMPTZ,
    ends_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coupons_establishment_code UNIQUE (establishment_id, code),
    CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_coupons_value CHECK (discount_value > 0),
    CONSTRAINT ck_coupons_min_order CHECK (min_order_amount IS NULL OR min_order_amount >= 0),
    CONSTRAINT ck_coupons_max_discount CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0),
    CONSTRAINT ck_coupons_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT ck_coupons_used_count CHECK (used_count >= 0)
);

CREATE INDEX idx_coupons_establishment_active ON coupons (establishment_id, active);

CREATE TRIGGER trg_coupons_updated_at
    BEFORE UPDATE ON coupons
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE orders
    ADD COLUMN coupon_id UUID REFERENCES coupons (id),
    ADD COLUMN coupon_code VARCHAR(40);

CREATE INDEX idx_orders_coupon_id ON orders (coupon_id);

CREATE TABLE stock_movements (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id      UUID NOT NULL REFERENCES establishments (id),
    product_id            UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    order_id              UUID REFERENCES orders (id) ON DELETE SET NULL,
    movement_type         VARCHAR(24) NOT NULL,
    quantity_delta        NUMERIC(12, 3) NOT NULL,
    quantity_after        NUMERIC(12, 3),
    note                  VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_stock_movements_type CHECK (movement_type IN ('SALE', 'ADJUSTMENT', 'RESTOCK'))
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_stock_movements_establishment ON stock_movements (establishment_id, created_at DESC);
