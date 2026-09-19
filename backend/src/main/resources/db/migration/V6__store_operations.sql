-- Funcionamento da loja, mídia e pedido mínimo
ALTER TABLE establishments
    ADD COLUMN IF NOT EXISTS cover_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS store_open_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    ADD COLUMN IF NOT EXISTS rating_avg NUMERIC(3, 2),
    ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0;

UPDATE establishments
SET opening_hours = '{
  "mon":[{"open":"08:00","close":"18:00"}],
  "tue":[{"open":"08:00","close":"18:00"}],
  "wed":[{"open":"08:00","close":"18:00"}],
  "thu":[{"open":"08:00","close":"18:00"}],
  "fri":[{"open":"08:00","close":"18:00"}],
  "sat":[{"open":"08:00","close":"18:00"}],
  "sun":[]
}'::jsonb
WHERE opening_hours IS NULL;

ALTER TABLE establishment_delivery_settings
    ADD COLUMN IF NOT EXISTS min_order_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS pickup_eta_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS delivery_eta_minutes INTEGER;

UPDATE establishment_delivery_settings
SET pickup_eta_minutes = COALESCE(pickup_eta_minutes, estimated_minutes),
    delivery_eta_minutes = COALESCE(delivery_eta_minutes, estimated_minutes);
