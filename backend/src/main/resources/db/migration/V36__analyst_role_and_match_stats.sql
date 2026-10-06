-- Analyst is an admin-assigned role. Team counters and possession live beside
-- the match, not in the goal protocol or the referee foul count.

ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('FAN', 'PLAYER', 'CAPTAIN', 'REFEREE', 'ANALYST', 'ADMIN'));

ALTER TABLE user_roles DROP CONSTRAINT ck_user_roles_role;
ALTER TABLE user_roles ADD CONSTRAINT ck_user_roles_role
    CHECK (role IN ('FAN', 'PLAYER', 'CAPTAIN', 'REFEREE', 'ANALYST', 'ADMIN'));

CREATE TABLE match_analyst_stats (
    match_id                    UUID PRIMARY KEY,
    home_shots                  INTEGER NOT NULL DEFAULT 0,
    home_shots_on_target        INTEGER NOT NULL DEFAULT 0,
    home_saves                  INTEGER NOT NULL DEFAULT 0,
    home_corners                INTEGER NOT NULL DEFAULT 0,
    home_fouls                  INTEGER NOT NULL DEFAULT 0,
    home_free_kicks             INTEGER NOT NULL DEFAULT 0,
    home_kick_ins               INTEGER NOT NULL DEFAULT 0,
    home_woodwork               INTEGER NOT NULL DEFAULT 0,
    home_possession_seconds     INTEGER NOT NULL DEFAULT 0,
    away_shots                  INTEGER NOT NULL DEFAULT 0,
    away_shots_on_target        INTEGER NOT NULL DEFAULT 0,
    away_saves                  INTEGER NOT NULL DEFAULT 0,
    away_corners                INTEGER NOT NULL DEFAULT 0,
    away_fouls                  INTEGER NOT NULL DEFAULT 0,
    away_free_kicks             INTEGER NOT NULL DEFAULT 0,
    away_kick_ins               INTEGER NOT NULL DEFAULT 0,
    away_woodwork               INTEGER NOT NULL DEFAULT 0,
    away_possession_seconds     INTEGER NOT NULL DEFAULT 0,
    possession_side             VARCHAR(16),
    possession_since            TIMESTAMPTZ,
    possession_tracked          BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_match_analyst_stats_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT ck_match_analyst_stats_side CHECK (
        possession_side IS NULL OR possession_side IN ('HOME', 'AWAY', 'PAUSED')
    ),
    CONSTRAINT ck_match_analyst_stats_nonneg CHECK (
        home_shots >= 0 AND home_shots_on_target >= 0 AND home_shots >= home_shots_on_target
        AND home_saves >= 0 AND home_corners >= 0 AND home_fouls >= 0
        AND home_free_kicks >= 0 AND home_kick_ins >= 0 AND home_woodwork >= 0
        AND home_possession_seconds >= 0
        AND away_shots >= 0 AND away_shots_on_target >= 0 AND away_shots >= away_shots_on_target
        AND away_saves >= 0 AND away_corners >= 0 AND away_fouls >= 0
        AND away_free_kicks >= 0 AND away_kick_ins >= 0 AND away_woodwork >= 0
        AND away_possession_seconds >= 0
    )
);
