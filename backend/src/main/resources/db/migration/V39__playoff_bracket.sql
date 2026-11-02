-- Group qualification places and a playoff bracket on top of tournament tables.
-- Matches keep the venue column from V30. A group match points at its table;
-- a playoff match points at a bracket round. One match is never both.

ALTER TABLE tournaments
    ADD COLUMN competition_stage VARCHAR(16) NOT NULL DEFAULT 'GROUPS';

ALTER TABLE tournaments
    ADD CONSTRAINT ck_tournaments_stage CHECK (competition_stage IN ('GROUPS', 'PLAYOFF'));

ALTER TABLE matches
    ADD COLUMN table_id UUID,
    ADD COLUMN bracket_round INTEGER;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_table FOREIGN KEY (table_id) REFERENCES tournament_tables (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_matches_bracket_round CHECK (bracket_round IS NULL OR bracket_round >= 1),
    ADD CONSTRAINT ck_matches_group_or_bracket CHECK (table_id IS NULL OR bracket_round IS NULL);

CREATE INDEX idx_matches_table_id ON matches (table_id);

CREATE TABLE tournament_advancing_places (
    id              UUID PRIMARY KEY,
    tournament_id   UUID    NOT NULL,
    place           INTEGER NOT NULL,
    CONSTRAINT fk_advancing_places_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
    CONSTRAINT uk_advancing_place UNIQUE (tournament_id, place),
    CONSTRAINT ck_advancing_place CHECK (place BETWEEN 1 AND 16)
);

CREATE TABLE playoff_slots (
    id                  UUID PRIMARY KEY,
    tournament_id       UUID         NOT NULL,
    round_number        INTEGER      NOT NULL,
    slot_index          INTEGER      NOT NULL,
    match_id            UUID,
    home_team_id        UUID,
    away_team_id        UUID,
    home_group_name     VARCHAR(200),
    home_place          INTEGER,
    away_group_name     VARCHAR(200),
    away_place          INTEGER,
    scheduled_at        TIMESTAMPTZ,
    venue               VARCHAR(120),
    CONSTRAINT fk_playoff_slots_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
    CONSTRAINT fk_playoff_slots_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_playoff_slots_home FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_playoff_slots_away FOREIGN KEY (away_team_id) REFERENCES teams (id),
    CONSTRAINT uk_playoff_slot UNIQUE (tournament_id, round_number, slot_index),
    CONSTRAINT uk_playoff_slot_match UNIQUE (match_id),
    CONSTRAINT ck_playoff_round CHECK (round_number >= 1),
    CONSTRAINT ck_playoff_slot_index CHECK (slot_index >= 0),
    CONSTRAINT ck_playoff_slot_teams CHECK (
        home_team_id IS NULL OR away_team_id IS NULL OR home_team_id <> away_team_id
    )
);

CREATE INDEX idx_playoff_slots_tournament_id ON playoff_slots (tournament_id);
