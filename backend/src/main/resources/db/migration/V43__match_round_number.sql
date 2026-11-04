-- Optional calendar tour. Not unique: dev already has duplicate fixtures, and a
-- unique key would fail the migration. Import matches them in the service.
ALTER TABLE matches
    ADD COLUMN round_number INTEGER;

ALTER TABLE matches
    ADD CONSTRAINT ck_matches_round_number CHECK (round_number IS NULL OR round_number >= 1);
