ALTER TABLE teams
    ADD COLUMN disbanded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_teams_disbanded ON teams (disbanded);
