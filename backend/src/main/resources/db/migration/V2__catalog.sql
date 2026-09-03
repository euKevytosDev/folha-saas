-- Folha FASE 3: categorias e produtos por estabelecimento.

CREATE TABLE categories (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID NOT NULL REFERENCES establishments (id),
    name               VARCHAR(120) NOT NULL,
    description        TEXT,
    image_url          VARCHAR(500),
    sort_order         INTEGER NOT NULL DEFAULT 0,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_categories_establishment_name UNIQUE (establishment_id, name)
);

CREATE INDEX idx_categories_establishment_id ON categories (establishment_id);
CREATE INDEX idx_categories_establishment_active ON categories (establishment_id, active, sort_order);

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id   UUID NOT NULL REFERENCES establishments (id),
    category_id        UUID NOT NULL REFERENCES categories (id),
    name               VARCHAR(160) NOT NULL,
    description        TEXT,
    image_url          VARCHAR(500),
    price              NUMERIC(12, 2) NOT NULL,
    compare_at_price   NUMERIC(12, 2),
    unit               VARCHAR(8) NOT NULL,
    available          BOOLEAN NOT NULL DEFAULT TRUE,
    featured           BOOLEAN NOT NULL DEFAULT FALSE,
    stock_controlled   BOOLEAN NOT NULL DEFAULT FALSE,
    stock_quantity     NUMERIC(12, 3),
    minimum_quantity   NUMERIC(12, 3) NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_compare CHECK (compare_at_price IS NULL OR compare_at_price >= 0),
    CONSTRAINT ck_products_unit CHECK (unit IN ('UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT')),
    CONSTRAINT ck_products_min_qty CHECK (minimum_quantity > 0),
    CONSTRAINT ck_products_stock CHECK (stock_quantity IS NULL OR stock_quantity >= 0)
);

CREATE INDEX idx_products_establishment_id ON products (establishment_id);
CREATE INDEX idx_products_establishment_category ON products (establishment_id, category_id);
CREATE INDEX idx_products_establishment_available ON products (establishment_id, available);
CREATE INDEX idx_products_establishment_featured ON products (establishment_id, featured);

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
