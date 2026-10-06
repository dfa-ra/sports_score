ALTER TABLE matches
    ADD COLUMN player_of_the_match_id UUID;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_player_of_the_match
        FOREIGN KEY (player_of_the_match_id) REFERENCES player_profiles (id);
