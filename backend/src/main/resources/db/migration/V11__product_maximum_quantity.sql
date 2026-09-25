-- Quantidade máxima de compra por produto (opcional).

ALTER TABLE products
    ADD COLUMN maximum_quantity NUMERIC(12, 3);

ALTER TABLE products
    ADD CONSTRAINT ck_products_max_qty CHECK (
        maximum_quantity IS NULL OR maximum_quantity > 0
    );

ALTER TABLE products
    ADD CONSTRAINT ck_products_max_gte_min CHECK (
        maximum_quantity IS NULL OR maximum_quantity >= minimum_quantity
    );
