-- ============================================================================
-- V7: Tabla de tokens JWT revocados (logout y revocación)
-- ============================================================================
-- Permite invalidar tokens antes de su expiración (logout real, cambio de rol,
-- suspensión de cuenta, etc.). El filtro JWT consulta esta tabla en cada petición.
-- ============================================================================

CREATE TABLE IF NOT EXISTS revoked_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti        VARCHAR(255) NOT NULL UNIQUE,        -- identificador único del JWT
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL                 -- fecha de expiración original del token
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_jti ON revoked_tokens(jti);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);

COMMENT ON TABLE revoked_tokens IS 'Lista negra de JWT revocados antes de su expiración';
