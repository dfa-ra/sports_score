CREATE TABLE match_availability (
    id          UUID PRIMARY KEY,
    match_id    UUID         NOT NULL,
    player_id   UUID         NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_match_availability_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_availability_player FOREIGN KEY (player_id) REFERENCES player_profiles (id),
    CONSTRAINT uk_match_availability_match_player UNIQUE (match_id, player_id),
    CONSTRAINT ck_match_availability_status CHECK (status IN ('GOING', 'NOT_GOING'))
);

CREATE INDEX idx_match_availability_match_id ON match_availability (match_id);
