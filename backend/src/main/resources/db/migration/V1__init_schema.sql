-- Student League initial schema
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('FAN', 'PLAYER', 'CAPTAIN', 'REFEREE', 'ADMIN'))
);

CREATE TABLE player_profiles (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    display_name    VARCHAR(150),
    date_of_birth   DATE,
    avatar_url      VARCHAR(1024),
    jersey_number   INTEGER,
    position        VARCHAR(64),
    bio             TEXT,
    CONSTRAINT uk_player_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_player_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE teams (
    id              UUID PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    short_name      VARCHAR(32),
    logo_url        VARCHAR(1024),
    captain_id      UUID,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_teams_captain FOREIGN KEY (captain_id) REFERENCES player_profiles (id)
);

CREATE TABLE team_members (
    id              UUID PRIMARY KEY,
    team_id         UUID         NOT NULL,
    player_id       UUID         NOT NULL,
    joined_at       TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_members_player FOREIGN KEY (player_id) REFERENCES player_profiles (id),
    CONSTRAINT ck_team_members_status CHECK (status IN ('ACTIVE', 'INVITED', 'REMOVED'))
);

CREATE INDEX idx_team_members_team_id ON team_members (team_id);
CREATE INDEX idx_team_members_player_id ON team_members (player_id);

CREATE TABLE sports (
    id              UUID PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(32)  NOT NULL,
    CONSTRAINT uk_sports_code UNIQUE (code)
);

CREATE TABLE tournaments (
    id              UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    sport_id        UUID         NOT NULL,
    season_year     INTEGER      NOT NULL,
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(32)  NOT NULL,
    format          VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_tournaments_sport FOREIGN KEY (sport_id) REFERENCES sports (id),
    CONSTRAINT ck_tournaments_status CHECK (status IN ('DRAFT', 'REGISTRATION', 'ACTIVE', 'FINISHED', 'CANCELLED'))
);

CREATE TABLE tournament_teams (
    id              UUID PRIMARY KEY,
    tournament_id   UUID         NOT NULL,
    team_id         UUID         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    registered_at   TIMESTAMPTZ  NOT NULL,
    approved_at     TIMESTAMPTZ,
    CONSTRAINT fk_tournament_teams_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
    CONSTRAINT fk_tournament_teams_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT uk_tournament_teams UNIQUE (tournament_id, team_id),
    CONSTRAINT ck_tournament_teams_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX idx_tournament_teams_tournament_id ON tournament_teams (tournament_id);

CREATE TABLE matches (
    id                  UUID PRIMARY KEY,
    tournament_id       UUID         NOT NULL,
    sport_id            UUID         NOT NULL,
    home_team_id        UUID         NOT NULL,
    away_team_id        UUID         NOT NULL,
    scheduled_at        TIMESTAMPTZ  NOT NULL,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    status              VARCHAR(32)  NOT NULL,
    home_score          INTEGER      NOT NULL DEFAULT 0,
    away_score          INTEGER      NOT NULL DEFAULT 0,
    game_time_seconds   INTEGER,
    period              INTEGER,
    CONSTRAINT fk_matches_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
    CONSTRAINT fk_matches_sport FOREIGN KEY (sport_id) REFERENCES sports (id),
    CONSTRAINT fk_matches_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id),
    CONSTRAINT ck_matches_status CHECK (status IN ('SCHEDULED', 'LIVE', 'PAUSED', 'FINISHED', 'CANCELLED')),
    CONSTRAINT ck_matches_different_teams CHECK (home_team_id <> away_team_id)
);

CREATE INDEX idx_matches_tournament_id ON matches (tournament_id);
CREATE INDEX idx_matches_scheduled_at ON matches (scheduled_at);
CREATE INDEX idx_matches_status ON matches (status);

CREATE TABLE match_referees (
    id              UUID PRIMARY KEY,
    match_id        UUID         NOT NULL,
    referee_id      UUID         NOT NULL,
    assigned_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_match_referees_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_referees_referee FOREIGN KEY (referee_id) REFERENCES users (id),
    CONSTRAINT uk_match_referees UNIQUE (match_id, referee_id)
);

CREATE TABLE match_events (
    id                      UUID PRIMARY KEY,
    match_id                UUID         NOT NULL,
    event_type              VARCHAR(64)  NOT NULL,
    timestamp               TIMESTAMPTZ  NOT NULL,
    game_time               INTEGER,
    team_id                 UUID,
    player_id               UUID,
    secondary_player_id     UUID,
    metadata                JSONB,
    voided                  BOOLEAN      NOT NULL DEFAULT FALSE,
    voided_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_match_events_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_events_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_match_events_player FOREIGN KEY (player_id) REFERENCES player_profiles (id),
    CONSTRAINT fk_match_events_secondary_player FOREIGN KEY (secondary_player_id) REFERENCES player_profiles (id),
    CONSTRAINT ck_match_events_type CHECK (event_type IN (
        'GOAL', 'ASSIST', 'YELLOW_CARD', 'RED_CARD', 'FOUL',
        'SUBSTITUTION', 'POINT', 'PERIOD_START', 'PERIOD_END', 'OTHER'
    ))
);

CREATE INDEX idx_match_events_match_id ON match_events (match_id);
CREATE INDEX idx_match_events_player_id ON match_events (player_id);

CREATE TABLE refresh_tokens (
    id                      UUID PRIMARY KEY,
    user_id                 UUID         NOT NULL,
    token_hash              VARCHAR(128) NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID,
    created_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
