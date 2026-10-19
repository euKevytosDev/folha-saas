ALTER TABLE establishments
    ADD COLUMN whatsapp_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN whatsapp_alert_phone VARCHAR(32),
    ADD COLUMN whatsapp_alert_apikey VARCHAR(80);
