CREATE TABLE password_reset_codes (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE password_reset_codes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON password_reset_codes FROM anon, authenticated;
