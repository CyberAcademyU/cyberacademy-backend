-- Aplicar antes de desplegar el backend: Flyway está desactivado.
CREATE TABLE auth_session_families (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false
);
CREATE INDEX auth_session_families_expiry ON auth_session_families(expires_at);
CREATE INDEX auth_session_families_user ON auth_session_families(user_id);
CREATE TABLE auth_session_tokens (
    refresh_hash varchar(64) PRIMARY KEY,
    access_hash varchar(64) NOT NULL UNIQUE,
    family_id uuid NOT NULL REFERENCES auth_session_families(id) ON DELETE CASCADE,
    access_expires_at timestamptz NOT NULL,
    used boolean NOT NULL DEFAULT false
);
CREATE INDEX auth_session_tokens_family ON auth_session_tokens(family_id);
ALTER TABLE auth_session_families ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_session_tokens ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON auth_session_families, auth_session_tokens FROM anon, authenticated;
