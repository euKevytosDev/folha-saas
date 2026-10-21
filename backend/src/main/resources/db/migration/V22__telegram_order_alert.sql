ALTER TABLE establishments
    ADD COLUMN telegram_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN telegram_chat_id VARCHAR(32),
    ADD COLUMN telegram_bot_token VARCHAR(160);
