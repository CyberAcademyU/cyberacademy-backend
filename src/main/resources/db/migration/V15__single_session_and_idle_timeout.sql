-- Aplicar manualmente ANTES de publicar el backend. Flyway está desactivado.
BEGIN;
ALTER TABLE auth_session_families
    ADD COLUMN last_activity_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Mantener solo la sesión más reciente por cuenta durante la transición.
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS position
    FROM auth_session_families WHERE revoked = false
)
UPDATE auth_session_families f SET revoked = true
FROM ranked r WHERE f.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX auth_session_one_active_per_user
    ON auth_session_families(user_id) WHERE revoked = false;
COMMIT;
