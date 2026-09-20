-- Snapshot da foto do produto no item do pedido.
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
