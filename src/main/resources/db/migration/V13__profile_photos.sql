CREATE TABLE profile_photos (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    version UUID NOT NULL UNIQUE,
    image BYTEA NOT NULL CHECK (octet_length(image) <= 262144)
);
