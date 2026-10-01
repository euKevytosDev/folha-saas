ALTER TABLE establishments
    ADD COLUMN schedule_when_closed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE establishment_payment_settings
    ADD COLUMN pay_on_delivery_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN scheduled_for TIMESTAMPTZ;
