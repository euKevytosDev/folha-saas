-- Sabores estilo iFood: preço opcional no sabor + mín/máx de escolhas no produto.

ALTER TABLE product_variants
    ALTER COLUMN price DROP NOT NULL;

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS ck_product_variants_price;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_price CHECK (price IS NULL OR price > 0);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS variant_min_choices INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS variant_max_choices INT NOT NULL DEFAULT 1;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS ck_products_variant_choices;

ALTER TABLE products
    ADD CONSTRAINT ck_products_variant_choices CHECK (
        variant_min_choices >= 1
        AND variant_max_choices >= variant_min_choices
    );

ALTER TABLE order_items
    ALTER COLUMN variant_name TYPE VARCHAR(500);
