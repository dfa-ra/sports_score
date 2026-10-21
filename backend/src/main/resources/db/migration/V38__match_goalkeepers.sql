ALTER TABLE matches
    ADD COLUMN home_goalkeeper_id UUID;

ALTER TABLE matches
    ADD COLUMN away_goalkeeper_id UUID;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_home_goalkeeper
        FOREIGN KEY (home_goalkeeper_id) REFERENCES player_profiles (id);

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_away_goalkeeper
        FOREIGN KEY (away_goalkeeper_id) REFERENCES player_profiles (id);
