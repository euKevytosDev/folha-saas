-- Sabores / opções de produto (variantes com preço próprio).

CREATE TABLE product_variants (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    price              NUMERIC(12, 2) NOT NULL,
    available          BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order         INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_product_variants_price CHECK (price > 0)
);

CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
CREATE INDEX idx_product_variants_product_sort ON product_variants (product_id, sort_order);

CREATE TRIGGER trg_product_variants_updated_at
    BEFORE UPDATE ON product_variants
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE order_items
    ADD COLUMN variant_id UUID REFERENCES product_variants (id) ON DELETE SET NULL,
    ADD COLUMN variant_name VARCHAR(120);

CREATE INDEX idx_order_items_variant_id ON order_items (variant_id);
