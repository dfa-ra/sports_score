ALTER TABLE matches
    ADD COLUMN period_count INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN period_length_seconds INTEGER NOT NULL DEFAULT 1200,
    ADD COLUMN clock_running_since TIMESTAMPTZ;

ALTER TABLE matches
    ADD CONSTRAINT ck_matches_period_count CHECK (period_count BETWEEN 1 AND 8),
    ADD CONSTRAINT ck_matches_period_length CHECK (period_length_seconds BETWEEN 60 AND 5400);

ALTER TABLE match_events
    ADD COLUMN period INTEGER;

CREATE TABLE match_lineup_players (
    id          UUID PRIMARY KEY,
    match_id    UUID    NOT NULL,
    team_id     UUID    NOT NULL,
    player_id   UUID    NOT NULL,
    starter     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_match_lineup_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_lineup_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_match_lineup_player FOREIGN KEY (player_id) REFERENCES player_profiles (id),
    CONSTRAINT uk_match_lineup_player UNIQUE (match_id, player_id)
);

CREATE INDEX idx_match_lineup_match ON match_lineup_players (match_id);
CREATE INDEX idx_match_lineup_team ON match_lineup_players (match_id, team_id);
