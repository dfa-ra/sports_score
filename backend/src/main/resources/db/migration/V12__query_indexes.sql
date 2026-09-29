CREATE INDEX idx_matches_tournament_status ON matches (tournament_id, status);

CREATE INDEX idx_player_profiles_last_name_lower ON player_profiles (lower(last_name));
CREATE INDEX idx_player_profiles_first_name_lower ON player_profiles (lower(first_name));
CREATE INDEX idx_player_profiles_display_name_lower ON player_profiles (lower(display_name));
