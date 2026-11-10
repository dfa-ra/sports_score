-- Per-group playoff qualification. One place belongs to one outcome.
-- Existing advancing_places and already opened brackets stay as they are.
-- A bye slot keeps the bracket index math when a team skips a round.

CREATE TABLE playoff_qualification_rules (
    id              UUID PRIMARY KEY,
    tournament_id   UUID         NOT NULL,
    table_id        UUID         NOT NULL,
    place_from      INTEGER      NOT NULL,
    place_to        INTEGER      NOT NULL,
    stage           VARCHAR(16)  NOT NULL,
    CONSTRAINT fk_qualify_rules_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
    CONSTRAINT fk_qualify_rules_table FOREIGN KEY (table_id) REFERENCES tournament_tables (id),
    CONSTRAINT ck_qualify_place CHECK (place_from >= 1 AND place_to >= place_from AND place_to <= 64),
    CONSTRAINT ck_qualify_stage CHECK (stage IN ('R16', 'R8', 'QF', 'SF', 'SILVER', 'OUT'))
);

CREATE INDEX idx_qualify_rules_tournament ON playoff_qualification_rules (tournament_id);
CREATE INDEX idx_qualify_rules_table ON playoff_qualification_rules (table_id);

ALTER TABLE playoff_slots
    ADD COLUMN bye BOOLEAN NOT NULL DEFAULT FALSE;
