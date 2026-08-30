-- Multi-role students, team founding date, tournament regulations, gallery

ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN photo_url VARCHAR(1024);

CREATE TABLE user_roles (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    photo_url       VARCHAR(1024),
    requested_at    TIMESTAMPTZ  NOT NULL,
    reviewed_at     TIMESTAMPTZ,
    review_note     VARCHAR(500),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('FAN', 'PLAYER', 'CAPTAIN', 'REFEREE', 'ADMIN')),
    CONSTRAINT ck_user_roles_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_status ON user_roles (status);

INSERT INTO user_roles (id, user_id, role, status, requested_at, reviewed_at)
SELECT gen_random_uuid(), id, role, 'APPROVED', created_at, updated_at
FROM users;

ALTER TABLE teams
    ADD COLUMN founded_on DATE;

ALTER TABLE tournaments
    ADD COLUMN regulations TEXT,
    ADD COLUMN max_squad_size INTEGER;

CREATE TABLE gallery_photos (
    id          UUID PRIMARY KEY,
    url         VARCHAR(2048) NOT NULL,
    caption     VARCHAR(300),
    source      VARCHAR(32)   NOT NULL,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_gallery_source CHECK (source IN ('UPLOAD', 'URL', 'VK'))
);

CREATE TABLE site_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value TEXT
);
