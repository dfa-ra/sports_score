-- Who records team stats for a match: one analyst for both sides, or one per team.

CREATE TABLE match_analyst_assignments (
    match_id      UUID PRIMARY KEY,
    mode          VARCHAR(16) NOT NULL,
    both_user_id  UUID,
    home_user_id  UUID,
    away_user_id  UUID,
    CONSTRAINT fk_match_analyst_assignments_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_analyst_assignments_both FOREIGN KEY (both_user_id) REFERENCES users (id),
    CONSTRAINT fk_match_analyst_assignments_home FOREIGN KEY (home_user_id) REFERENCES users (id),
    CONSTRAINT fk_match_analyst_assignments_away FOREIGN KEY (away_user_id) REFERENCES users (id),
    CONSTRAINT ck_match_analyst_assignments_mode CHECK (mode IN ('BOTH', 'SPLIT')),
    CONSTRAINT ck_match_analyst_assignments_shape CHECK (
        (mode = 'BOTH' AND both_user_id IS NOT NULL AND home_user_id IS NULL AND away_user_id IS NULL)
        OR
        (mode = 'SPLIT' AND both_user_id IS NULL AND home_user_id IS NOT NULL AND away_user_id IS NOT NULL
            AND home_user_id <> away_user_id)
    )
);
