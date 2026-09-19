-- Folha FASE 4: clientes, pedidos e itens com snapshot de preço.

CREATE TABLE customers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID NOT NULL REFERENCES establishments (id),
    name               VARCHAR(160) NOT NULL,
    phone              VARCHAR(32) NOT NULL,
    email              VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_customers_establishment_phone UNIQUE (establishment_id, phone)
);

CREATE INDEX idx_customers_establishment_id ON customers (establishment_id);

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id     UUID NOT NULL REFERENCES establishments (id),
    customer_id          UUID NOT NULL REFERENCES customers (id),
    public_code          VARCHAR(12) NOT NULL,
    status               VARCHAR(32) NOT NULL,
    fulfillment_type     VARCHAR(16) NOT NULL,
    payment_method       VARCHAR(24) NOT NULL,
    customer_name        VARCHAR(160) NOT NULL,
    customer_phone       VARCHAR(32) NOT NULL,
    customer_email       VARCHAR(255),
    address_zip_code     VARCHAR(16),
    address_street       VARCHAR(255),
    address_number       VARCHAR(32),
    address_complement   VARCHAR(120),
    address_neighborhood VARCHAR(120),
    address_city         VARCHAR(120),
    address_state        CHAR(2),
    notes                TEXT,
    subtotal             NUMERIC(12, 2) NOT NULL,
    discount             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivery_fee         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total                NUMERIC(12, 2) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_orders_public_code UNIQUE (public_code),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PREPARING', 'DISPATCHED', 'DELIVERED', 'CANCELLED'
    )),
    CONSTRAINT ck_orders_fulfillment CHECK (fulfillment_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_orders_payment CHECK (payment_method IN ('PIX', 'CASH', 'CARD', 'ON_DELIVERY')),
    CONSTRAINT ck_orders_money CHECK (
        subtotal >= 0 AND discount >= 0 AND delivery_fee >= 0 AND total >= 0
    )
);

CREATE INDEX idx_orders_establishment_id ON orders (establishment_id);
CREATE INDEX idx_orders_establishment_status ON orders (establishment_id, status);
CREATE INDEX idx_orders_establishment_created ON orders (establishment_id, created_at DESC);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE order_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    establishment_id   UUID NOT NULL REFERENCES establishments (id),
    product_id         UUID REFERENCES products (id) ON DELETE SET NULL,
    product_name       VARCHAR(160) NOT NULL,
    product_unit       VARCHAR(8) NOT NULL,
    quantity           NUMERIC(12, 3) NOT NULL,
    unit_price         NUMERIC(12, 2) NOT NULL,
    subtotal           NUMERIC(12, 2) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_money CHECK (unit_price >= 0 AND subtotal >= 0),
    CONSTRAINT ck_order_items_unit CHECK (product_unit IN ('UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT'))
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_establishment_id ON order_items (establishment_id);
