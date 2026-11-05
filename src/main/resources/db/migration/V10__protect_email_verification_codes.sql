-- La aplicación nueva guarda HMAC-SHA-256 en hexadecimal (64 caracteres).
ALTER TABLE email_otps ALTER COLUMN otp_code TYPE VARCHAR(64);

-- Los códigos antiguos no pueden convertirse sin acceder al secreto de la aplicación.
-- Se invalidan y se elimina su valor en claro; el usuario puede solicitar otro.
UPDATE email_otps SET is_used = TRUE, otp_code = '' WHERE LENGTH(otp_code) = 6;
