# Database

## Policy

- **PostgreSQL** is the source of truth.
- All schema changes go through **Flyway** (`backend/src/main/resources/db/migration`).
- JPA `ddl-auto` is `validate` (or `none`) — never `create` / `update` in shared environments.
- Prefer UUIDs for primary keys.
- Avoid bidirectional serialization graphs; expose data via DTOs/projections.

## Entity-relationship overview

```
User 1──1 PlayerProfile
PlayerProfile 1──* TeamMember *──1 Team
Team.captainId → PlayerProfile
Sport 1──* Tournament 1──* TournamentTeam *──1 Team
Tournament 1──* Match
Sport 1──* Match
Team (home/away) → Match
Match *──* User(REFEREE) via MatchReferee
Match 1──* MatchEvent
User 1──* RefreshToken
```

## Tables

### users
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| email | VARCHAR UNIQUE NOT NULL | |
| password_hash | VARCHAR NOT NULL | BCrypt |
| role | VARCHAR NOT NULL | FAN, PLAYER, CAPTAIN, REFEREE, ADMIN |
| enabled | BOOLEAN NOT NULL | |
| created_at / updated_at | TIMESTAMPTZ | |

### player_profiles
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| user_id | UUID UNIQUE FK → users | |
| first_name, last_name, display_name | VARCHAR | |
| date_of_birth | DATE | nullable |
| avatar_url | VARCHAR | S3 URL/key |
| jersey_number | INT | nullable |
| position | VARCHAR | sport-agnostic label |
| bio | TEXT | |

### teams
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR NOT NULL | |
| short_name | VARCHAR | |
| logo_url | VARCHAR | |
| captain_id | UUID FK → player_profiles | nullable |
| created_at / updated_at | TIMESTAMPTZ | |

### team_members
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| team_id | UUID FK | indexed |
| player_id | UUID FK | indexed |
| joined_at | TIMESTAMPTZ | |
| status | VARCHAR | ACTIVE, INVITED, REMOVED |

### sports
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR | |
| code | VARCHAR UNIQUE | FOOTBALL, BASKETBALL, VOLLEYBALL, HOCKEY |

### tournaments
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR | |
| description | TEXT | |
| sport_id | UUID FK | |
| season_year | INT | stats filter |
| start_date / end_date | DATE | |
| status | VARCHAR | DRAFT, REGISTRATION, ACTIVE, FINISHED, CANCELLED |
| format | VARCHAR | e.g. ROUND_ROBIN, KNOCKOUT |
| created_at / updated_at | TIMESTAMPTZ | |

### tournament_teams
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tournament_id | UUID FK | indexed |
| team_id | UUID FK | |
| status | VARCHAR | PENDING, APPROVED, REJECTED, WITHDRAWN |
| registered_at / approved_at | TIMESTAMPTZ | |
| UNIQUE(tournament_id, team_id) | | |

### matches
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tournament_id | UUID FK | indexed |
| sport_id | UUID FK | |
| home_team_id / away_team_id | UUID FK | |
| scheduled_at | TIMESTAMPTZ | indexed |
| started_at / finished_at | TIMESTAMPTZ | |
| status | VARCHAR | SCHEDULED, LIVE, PAUSED, FINISHED, CANCELLED — indexed |
| home_score / away_score | INT | derived from events |
| game_time_seconds | INT | nullable |
| period | INT | nullable |

### match_referees
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| match_id | UUID FK | |
| referee_id | UUID FK → users | must be REFEREE |
| assigned_at | TIMESTAMPTZ | |
| UNIQUE(match_id, referee_id) | | |

### match_events
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| match_id | UUID FK | indexed |
| event_type | VARCHAR | GOAL, ASSIST, YELLOW_CARD, … |
| timestamp | TIMESTAMPTZ | wall clock |
| game_time | INT | seconds or sport-specific unit |
| team_id | UUID | nullable |
| player_id | UUID | indexed; nullable |
| secondary_player_id | UUID | assist / sub partner |
| metadata | JSONB | sport-specific payload |
| voided | BOOLEAN | soft cancel |
| voided_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | |

### refresh_tokens
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | |
| token_hash | VARCHAR UNIQUE | SHA-256 of opaque token |
| expires_at | TIMESTAMPTZ | |
| revoked_at | TIMESTAMPTZ | |
| replaced_by_token_id | UUID | rotation chain |
| created_at | TIMESTAMPTZ | |

## Indexes (required)

- `matches(tournament_id)`, `matches(scheduled_at)`, `matches(status)`
- `match_events(match_id)`, `match_events(player_id)`
- `team_members(team_id)`, `team_members(player_id)`
- `tournament_teams(tournament_id)`

## Enumerations (stored as VARCHAR)

**Role:** FAN, PLAYER, CAPTAIN, REFEREE, ADMIN  

**TeamMemberStatus:** ACTIVE, INVITED, REMOVED  

**TournamentStatus:** DRAFT, REGISTRATION, ACTIVE, FINISHED, CANCELLED  

**TournamentTeamStatus:** PENDING, APPROVED, REJECTED, WITHDRAWN  

**MatchStatus:** SCHEDULED, LIVE, PAUSED, FINISHED, CANCELLED  

**MatchEventType:** GOAL, ASSIST, YELLOW_CARD, RED_CARD, FOUL, SUBSTITUTION, POINT, PERIOD_START, PERIOD_END, OTHER

## JSONB metadata examples

Football goal:

```json
{ "period": 1, "ownGoal": false }
```

Substitution:

```json
{ "playerInId": "...", "playerOutId": "..." }
```

Basketball point:

```json
{ "points": 3, "period": 2 }
```

## Statistics

Computed from non-voided `match_events` and finished `matches`. Optional materialized/cache tables may be added later but must be rebuildable from events.
