-- Device tokens for push notification delivery
CREATE TABLE device_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    platform    VARCHAR(32)  NOT NULL,
    token       VARCHAR(512) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token),
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);
