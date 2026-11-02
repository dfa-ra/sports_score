-- Penalty shootout kicks for a drawn playoff match.
-- Rows are the series only. Match goals and player events stay where they are.

CREATE TABLE penalty_kicks (
    id          UUID PRIMARY KEY,
    match_id    UUID    NOT NULL,
    team_id     UUID    NOT NULL,
    kick_order  INTEGER NOT NULL,
    scored      BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_penalty_kicks_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_penalty_kicks_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT uk_penalty_kick_order UNIQUE (match_id, kick_order),
    CONSTRAINT ck_penalty_kick_order CHECK (kick_order >= 1)
);

CREATE INDEX idx_penalty_kicks_match_id ON penalty_kicks (match_id);
