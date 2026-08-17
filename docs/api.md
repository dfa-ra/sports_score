# API

Base URL: `/api/v1`  
Content-Type: `application/json`  
Auth: `Authorization: Bearer <access_token>`

Interactive docs: `/swagger-ui.html` (OpenAPI at `/v3/api-docs`).

## Conventions

### Pagination

List endpoints accept:

- `page` (0-based), `size`, `sort` (e.g. `scheduledAt,desc`)

Response envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Errors

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Human-readable summary",
  "details": [{ "field": "email", "message": "must be a well-formed email address" }],
  "timestamp": "2026-08-17T12:00:00Z",
  "path": "/api/v1/auth/register"
}
```

Common codes: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `BUSINESS_RULE_VIOLATION`.

### Filtering

Where applicable: query params such as `tournamentId`, `status`, `teamId`, `playerId`, `seasonYear`, `sportCode`, `from`, `to`.

---

## Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Create FAN account |
| POST | `/auth/login` | Public | Access + refresh tokens |
| POST | `/auth/refresh` | Public (refresh body) | Rotate refresh, new access |
| POST | `/auth/logout` | Bearer or refresh | Revoke refresh token |
| GET | `/auth/me` | Bearer | Current user profile |

### Register

Request:

```json
{ "email": "fan@example.com", "password": "Str0ngPass!" }
```

Response `201`: user summary (id, email, role) — **no password**.

### Login

Request: `{ "email", "password" }`  

Response `200`:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "...", "email": "...", "role": "FAN" }
}
```

### Refresh

Request: `{ "refreshToken": "..." }` → new access + refresh (old refresh revoked).

Rate limiting applies to register/login/refresh.

---

## Health

| Method | Path | Auth |
|---|---|---|
| GET | `/health` | Public |

---

## Users (Phase 2+)

| Method | Path | Roles |
|---|---|---|
| GET | `/admin/users` | ADMIN |
| PATCH | `/admin/users/{id}` | ADMIN — enable/role |

---

## Players (Phase 2+)

| Method | Path | Auth |
|---|---|---|
| GET | `/players` | Authenticated |
| GET | `/players/{id}` | Authenticated |
| GET | `/players/{id}/card` | Authenticated/public — public card |
| POST/PUT | `/players/me` | PLAYER+ — own profile |

Public card: name, photo, team, number, position, statistics, match history.

---

## Teams (Phase 2+)

| Method | Path | Auth |
|---|---|---|
| GET/POST | `/teams` | Read: auth; Create: CAPTAIN/ADMIN |
| GET/PATCH | `/teams/{id}` | Patch: captain of team or ADMIN |
| GET | `/teams/{id}/members` | Auth |
| POST | `/teams/{id}/members` | Captain of team |
| DELETE | `/teams/{id}/members/{playerId}` | Captain of team |
| PUT | `/teams/{id}/captain` | Captain or ADMIN |

---

## Sports (Phase 2+)

| Method | Path | Auth |
|---|---|---|
| GET | `/sports` | Auth |

---

## Tournaments (Phase 3+)

| Method | Path | Auth |
|---|---|---|
| GET/POST | `/tournaments` | POST: ADMIN |
| GET/PATCH | `/tournaments/{id}` | PATCH: ADMIN |
| POST | `/tournaments/{id}/teams` | CAPTAIN — register own team |
| POST | `/tournaments/{id}/teams/{teamId}/approve` | ADMIN |
| DELETE | `/tournaments/{id}/teams/{teamId}` | ADMIN — exclude |
| GET | `/tournaments/{id}/standings` | Auth |
| GET | `/tournaments/{id}/matches` | Auth |

---

## Matches (Phase 3+)

| Method | Path | Auth |
|---|---|---|
| GET | `/matches` | Auth — filters |
| GET | `/matches/{id}` | Auth |
| POST | `/matches` | ADMIN |
| POST | `/matches/{id}/referees` | ADMIN |

---

## Referee / Match events (Phase 4+)

All referee actions require `currentUser` assigned on the match.

| Method | Path | Auth |
|---|---|---|
| GET | `/referee/matches` | REFEREE |
| POST | `/referee/matches/{id}/start` | Assigned referee |
| POST | `/referee/matches/{id}/pause` | Assigned referee |
| POST | `/referee/matches/{id}/resume` | Assigned referee |
| POST | `/referee/matches/{id}/finish` | Assigned referee |
| GET | `/matches/{id}/events` | Auth |
| POST | `/referee/matches/{id}/events` | Assigned referee |
| POST | `/referee/matches/{id}/events/{eventId}/void` | Assigned referee |

---

## Statistics (Phase 6+)

| Method | Path | Auth |
|---|---|---|
| GET | `/statistics/players` | Auth — filters |
| GET | `/statistics/teams` | Auth — filters |

Derived from non-voided `MatchEvent` records.

---

## WebSocket (Phase 5+)

- Connect: `ws://host/ws` (STOMP).
- Subscribe: `/topic/matches/{matchId}`.
- Auth: JWT on CONNECT.

Message example:

```json
{
  "type": "MATCH_UPDATE",
  "matchId": "...",
  "status": "LIVE",
  "homeScore": 1,
  "awayScore": 0,
  "gameTimeSeconds": 320,
  "lastEvent": { "eventType": "GOAL", "playerId": "...", "teamId": "..." }
}
```

---

## Notifications / Push

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/notifications/device-tokens` | Bearer | Register ANDROID/IOS/WEB device token |
| DELETE | `/notifications/device-tokens?token=` | Bearer | Unregister token |

Push delivery goes through `NotificationService` → `PushNotificationProvider` (no-op by default; optional FCM/APNs stubs).

Domain triggers: match starting/finished, goal, tournament registration, team invitation, schedule update.

---

## Uploads

| Method | Path | Auth |
|---|---|---|
| POST | `/uploads/players/me/avatar` | Authenticated (multipart `file`) |
| POST | `/uploads/teams/{teamId}/logo` | Captain of team or ADMIN |

---

## Authorization matrix (summary)

| Capability | FAN | PLAYER | CAPTAIN | REFEREE | ADMIN |
|---|---|---|---|---|---|
| Browse public data | ✓ | ✓ | ✓ | ✓ | ✓ |
| Manage own profile | | ✓ | ✓ | | ✓ |
| Manage own team roster | | | ✓* | | ✓ |
| Register team to tournament | | | ✓* | | ✓ |
| Create/edit tournaments | | | | | ✓ |
| Control assigned match | | | | ✓* | |
| Assign referees | | | | | ✓ |

\* Ownership checks required (captain of that team / assigned referee).
