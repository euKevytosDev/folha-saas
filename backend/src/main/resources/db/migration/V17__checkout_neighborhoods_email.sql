ALTER TABLE establishment_delivery_settings
    ADD COLUMN collect_email BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN delivery_neighborhoods TEXT;

UPDATE establishment_delivery_settings AS settings
SET collect_email = FALSE
FROM establishments AS store
WHERE store.id = settings.establishment_id
  AND store.slug = 'sacolao-abc';
