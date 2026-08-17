# База данных

## Политика

- **PostgreSQL** — источник истины.
- Все изменения схемы — только через **Flyway** (`backend/src/main/resources/db/migration`).
- JPA `ddl-auto` = `validate` (или `none`) — никогда `create` / `update` в общих окружениях.
- Предпочтительны UUID как PK.
- Избегать bidirectional serialization graphs; отдавать данные через DTO/projections.

## Обзор связей

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
User 1──* DeviceToken
```

## Таблицы

### users
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| email | VARCHAR UNIQUE NOT NULL | |
| password_hash | VARCHAR NOT NULL | BCrypt |
| role | VARCHAR NOT NULL | FAN, PLAYER, CAPTAIN, REFEREE, ADMIN |
| enabled | BOOLEAN NOT NULL | |
| created_at / updated_at | TIMESTAMPTZ | |

### player_profiles
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| user_id | UUID UNIQUE FK → users | |
| first_name, last_name, display_name | VARCHAR | |
| date_of_birth | DATE | nullable |
| avatar_url | VARCHAR | URL файла на диске (`/media/...`) |
| jersey_number | INT | nullable |
| position | VARCHAR | спорт-агностичная метка |
| bio | TEXT | |

### teams
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR NOT NULL | |
| short_name | VARCHAR | |
| logo_url | VARCHAR | |
| captain_id | UUID FK → player_profiles | nullable |
| created_at / updated_at | TIMESTAMPTZ | |

### team_members
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| team_id | UUID FK | индекс |
| player_id | UUID FK | индекс |
| joined_at | TIMESTAMPTZ | |
| status | VARCHAR | ACTIVE, INVITED, REMOVED |

### sports
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR | |
| code | VARCHAR UNIQUE | FOOTBALL, BASKETBALL, VOLLEYBALL, HOCKEY |

### tournaments
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR | |
| description | TEXT | |
| sport_id | UUID FK | |
| season_year | INT | фильтр статистики |
| start_date / end_date | DATE | |
| status | VARCHAR | DRAFT, REGISTRATION, ACTIVE, FINISHED, CANCELLED |
| format | VARCHAR | напр. ROUND_ROBIN, KNOCKOUT |
| created_at / updated_at | TIMESTAMPTZ | |

### tournament_teams
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| tournament_id | UUID FK | индекс |
| team_id | UUID FK | |
| status | VARCHAR | PENDING, APPROVED, REJECTED, WITHDRAWN |
| registered_at / approved_at | TIMESTAMPTZ | |
| UNIQUE(tournament_id, team_id) | | |

### matches
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| tournament_id | UUID FK | индекс |
| sport_id | UUID FK | |
| home_team_id / away_team_id | UUID FK | |
| scheduled_at | TIMESTAMPTZ | индекс |
| started_at / finished_at | TIMESTAMPTZ | |
| status | VARCHAR | SCHEDULED, LIVE, PAUSED, FINISHED, CANCELLED — индекс |
| home_score / away_score | INT | выводится из событий |
| game_time_seconds | INT | nullable |
| period | INT | nullable |

### match_referees
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| match_id | UUID FK | |
| referee_id | UUID FK → users | должен быть REFEREE |
| assigned_at | TIMESTAMPTZ | |
| UNIQUE(match_id, referee_id) | | |

### match_events
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| match_id | UUID FK | индекс |
| event_type | VARCHAR | GOAL, ASSIST, YELLOW_CARD, … |
| timestamp | TIMESTAMPTZ | wall clock |
| game_time | INT | секунды или единица спорта |
| team_id | UUID | nullable |
| player_id | UUID | индекс; nullable |
| secondary_player_id | UUID | ассист / партнёр замены |
| metadata | JSONB | спорт-специфичный payload |
| voided | BOOLEAN | soft-отмена |
| voided_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | |

### refresh_tokens
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | |
| token_hash | VARCHAR UNIQUE | SHA-256 opaque-токена |
| expires_at | TIMESTAMPTZ | |
| revoked_at | TIMESTAMPTZ | |
| replaced_by_token_id | UUID | цепочка ротации |
| created_at | TIMESTAMPTZ | |

### device_tokens
| Колонка | Тип | Примечание |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | |
| platform | VARCHAR | ANDROID, IOS, WEB |
| token | VARCHAR UNIQUE | |
| created_at / updated_at | TIMESTAMPTZ | |

## Индексы (обязательные)

- `matches(tournament_id)`, `matches(scheduled_at)`, `matches(status)`
- `match_events(match_id)`, `match_events(player_id)`
- `team_members(team_id)`, `team_members(player_id)`
- `tournament_teams(tournament_id)`

## Перечисления (хранятся как VARCHAR)

**Role:** FAN, PLAYER, CAPTAIN, REFEREE, ADMIN  

**TeamMemberStatus:** ACTIVE, INVITED, REMOVED  

**TournamentStatus:** DRAFT, REGISTRATION, ACTIVE, FINISHED, CANCELLED  

**TournamentTeamStatus:** PENDING, APPROVED, REJECTED, WITHDRAWN  

**MatchStatus:** SCHEDULED, LIVE, PAUSED, FINISHED, CANCELLED  

**MatchEventType:** GOAL, ASSIST, YELLOW_CARD, RED_CARD, FOUL, SUBSTITUTION, POINT, PERIOD_START, PERIOD_END, OTHER

## Примеры JSONB metadata

Гол в футболе:

```json
{ "period": 1, "ownGoal": false }
```

Замена:

```json
{ "playerInId": "...", "playerOutId": "..." }
```

Очко в баскетболе:

```json
{ "points": 3, "period": 2 }
```

## Статистика

Считается из не-voided `match_events` и завершённых `matches`. Materialized/cache таблицы допустимы позже, но должны полностью восстанавливаться из событий.
