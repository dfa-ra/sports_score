-- Google subject and optional password (Google-only accounts have no password yet).
ALTER TABLE users
    ADD COLUMN google_sub VARCHAR(255);

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);

ALTER TABLE users
    ADD CONSTRAINT ck_users_password_or_google
        CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL);

-- Single-use password reset tokens. Only the SHA-256 hash is stored.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
