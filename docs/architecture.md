# Architecture

## Overview

Student League is a **modular monolith**: a single Spring Boot 3 application partitioned into feature packages. Domain logic stays extractable for future service splits without rewriting business rules.

Clients:

- **Web** — Vue 3 SPA
- **Mobile** — Flutter (shared Android/iOS codebase)
- **Integrations** — OpenAPI consumers

## High-level diagram

```
┌─────────────┐  ┌─────────────┐
│  Vue Web    │  │ Flutter App │
└──────┬──────┘  └──────┬──────┘
       │ REST / WS       │
       ▼                 ▼
┌──────────────────────────────────┐
│     Spring Boot Modular Monolith │
│  auth users players teams sports │
│  tournaments matches referees    │
│  statistics notifications admin  │
└───────┬──────────┬──────────┬────┘
        │          │          │
        ▼          ▼          ▼
   PostgreSQL    Redis    S3-compatible
   (truth)     (cache/    (media)
               pubsub/
               rate limit)
```

## Module boundaries

| Module | Responsibility |
|---|---|
| `common` | Errors, pagination, shared types |
| `config` | Security, Redis, OpenAPI, WebSocket, CORS, Jackson |
| `security` | JWT, principals, filters, permission helpers |
| `auth` | Register, login, logout, refresh |
| `users` | User account administration |
| `players` | Player profiles and public cards |
| `teams` | Teams, membership, captaincy |
| `sports` | Sport catalog (FOOTBALL, …) |
| `tournaments` | Tournaments, team registration |
| `matches` | Matches, events, live control, score policies |
| `referees` | Referee assignment views |
| `statistics` | Aggregation from `MatchEvent` |
| `notifications` | Push abstraction (FCM/APNs) |
| `storage` | Object storage abstraction |
| `admin` | Cross-cutting admin APIs |

Each feature package uses layers: `controller` → `service` → `repository`, with `entity`, `dto`, `mapper`.

## Layering rules

1. Controllers accept/return DTOs only — never JPA entities.
2. Business rules live in services (transactions, ownership checks).
3. Repositories are Spring Data interfaces; avoid huge entity graphs.
4. Cross-module calls go through services (or small shared ports), not repositories of other features when avoidable.

## Authentication & authorization

- **Access token:** JWT (short-lived), Bearer header.
- **Refresh token:** opaque random value; only SHA-256 hash stored; **rotated** on each refresh.
- **Passwords:** BCrypt; `passwordHash` never appears in API responses.
- **Roles:** `FAN` < `PLAYER` < `CAPTAIN` via Spring `RoleHierarchy`. `REFEREE` and `ADMIN` are orthogonal.
- **Ownership:** captain checks use `Team.captainId` vs current user's `PlayerProfile`; referee checks use `MatchReferee` assignment. Client-supplied role/ids are never trusted for authz.

## Match events & live score

`MatchEvent` is the **source of truth** for scoring and statistics.

Flow after a referee action:

1. Validate referee assignment + match state machine + sport rules.
2. Persist `MatchEvent` (PostgreSQL).
3. Recalculate match score/status via sport-specific `ScorePolicy`.
4. Publish to Redis Pub/Sub channel `match:{matchId}`.
5. WebSocket bridge fans out to STOMP topic `/topic/matches/{matchId}`.

Clients subscribe once; polling is not required for live updates.

## WebSocket

- Endpoint: `/ws` (STOMP over SockJS/WebSocket).
- Subscribe: `/topic/matches/{matchId}`.
- Connect auth: JWT (query param or header interceptor).
- Payloads: score changes, new/voided events, status, game time, match finished.

## Redis usage

| Use | Notes |
|---|---|
| Cache | Read-heavy stats/lists with TTL (optional later) |
| Pub/Sub | Live match event propagation when `APP_REDIS_ENABLED=true` |
| Rate limiting | Auth endpoints (in-memory by default; Redis-ready) |
| Temporary data | Short-lived locks / ephemeral state |

Enable Redis live fan-out with `APP_REDIS_ENABLED=true`. Flow:

1. Persist `MatchEvent` / match state in PostgreSQL
2. `LiveMatchPublisher` publishes JSON to Redis channel `studentleague:match-live`
3. Each backend instance listens and fans out to local STOMP `/topic/matches/{matchId}`

When Redis is disabled, updates are published directly to the local STOMP broker (single-instance).

PostgreSQL remains the only durable source of truth.

## File storage

Player avatars and team logos are stored in S3-compatible storage. The database keeps `avatarUrl` / `logoUrl` (or object keys). `StorageService` abstracts the provider (MinIO locally, AWS S3 in production).

## Notifications

`NotificationPublisher` / `PushNotificationProvider` interfaces allow swapping FCM, APNs, or a no-op provider. Domain services emit notification intents; providers deliver them asynchronously.

## Deployment

Local/dev: Docker Compose (`backend`, `postgres`, `redis`, later `minio`).

Production: same containers behind a reverse proxy; secrets via environment; Flyway on startup; `ddl-auto=validate` (never `create`).

## Future extraction

Feature packages map 1:1 to candidate services. Shared kernel (`common`, auth contracts) would become libraries; Redis/WS already assume multi-instance fan-out.
