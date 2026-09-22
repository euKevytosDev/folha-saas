-- CPF no pedido, token de visualização pública, NCM por produto.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_cpf VARCHAR(11);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS view_token VARCHAR(48);

UPDATE orders
SET view_token = substr(replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''), 1, 36)
WHERE view_token IS NULL OR view_token = '';

ALTER TABLE orders
    ALTER COLUMN view_token SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_view_token ON orders (view_token);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS ncm VARCHAR(8);
