-- Technical forfeit. Goals are a score only: protocol events stay in the table
-- and are left out of statistics while the forfeit is active.
-- pre_forfeit_* is the exact score and status restored on cancel.

ALTER TABLE matches
    ADD COLUMN forfeit VARCHAR(16) NOT NULL DEFAULT 'none',
    ADD COLUMN forfeit_reason VARCHAR(32),
    ADD COLUMN forfeit_reason_note VARCHAR(500),
    ADD COLUMN forfeit_set_by UUID,
    ADD COLUMN forfeit_set_at TIMESTAMPTZ,
    ADD COLUMN pre_forfeit_status VARCHAR(32),
    ADD COLUMN pre_forfeit_home_score INTEGER,
    ADD COLUMN pre_forfeit_away_score INTEGER,
    ADD COLUMN pre_forfeit_finished_at TIMESTAMPTZ;

ALTER TABLE matches
    ADD CONSTRAINT ck_matches_forfeit CHECK (forfeit IN ('none', 'home', 'away', 'both')),
    ADD CONSTRAINT ck_matches_forfeit_reason CHECK (
        forfeit_reason IS NULL OR forfeit_reason IN ('no_show', 'incomplete', 'other')
    ),
    ADD CONSTRAINT ck_matches_forfeit_snapshot CHECK (
        (forfeit = 'none'
            AND forfeit_reason IS NULL
            AND pre_forfeit_status IS NULL
            AND pre_forfeit_home_score IS NULL
            AND pre_forfeit_away_score IS NULL)
        OR (forfeit <> 'none'
            AND forfeit_reason IS NOT NULL
            AND pre_forfeit_status IS NOT NULL
            AND pre_forfeit_home_score IS NOT NULL
            AND pre_forfeit_away_score IS NOT NULL)
    ),
    ADD CONSTRAINT fk_matches_forfeit_set_by
        FOREIGN KEY (forfeit_set_by) REFERENCES users (id) ON DELETE SET NULL;
