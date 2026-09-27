CREATE TABLE tournament_tables (
    id              UUID PRIMARY KEY,
    tournament_id   UUID         NOT NULL,
    name            VARCHAR(200) NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_tournament_tables_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
);

CREATE INDEX idx_tournament_tables_tournament_id ON tournament_tables (tournament_id);

ALTER TABLE tournament_teams
    ADD COLUMN table_id UUID;

ALTER TABLE tournament_teams
    ADD CONSTRAINT fk_tournament_teams_table
        FOREIGN KEY (table_id) REFERENCES tournament_tables (id) ON DELETE SET NULL;
