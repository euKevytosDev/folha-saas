-- Preço do sabor: EXTRA soma no produto; FIXED substitui o preço principal.

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS price_mode VARCHAR(16) NOT NULL DEFAULT 'EXTRA';

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS ck_product_variants_price_mode;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_price_mode CHECK (price_mode IN ('EXTRA', 'FIXED'));

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS ck_product_variants_fixed_price;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_fixed_price CHECK (
        price_mode <> 'FIXED' OR price IS NOT NULL
    );
