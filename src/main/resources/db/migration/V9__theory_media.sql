-- Ejecutar manualmente cuando Flyway esté desactivado.
ALTER TABLE module_sections ADD COLUMN image_url_1 VARCHAR(2048);
ALTER TABLE module_sections ADD COLUMN image_url_2 VARCHAR(2048);
ALTER TABLE module_sections ADD COLUMN video_url VARCHAR(2048);
