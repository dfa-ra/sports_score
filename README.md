# Student League

Production-oriented platform for a student sports league: live scores, schedules, statistics, and management of teams, players, referees, and tournaments.

Inspired by FlashScore for match viewing, with additional operational capabilities for captains, referees, and administrators.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, JPA/Hibernate, Flyway |
| Database | PostgreSQL (source of truth) |
| Cache / Pub-Sub | Redis |
| API | REST `/api/v1`, OpenAPI/Swagger, WebSocket (STOMP) |
| Web | Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios |
| Mobile | Flutter (Android + iOS) |
| Storage | S3-compatible object storage (avatars, logos) |
| Infra | Docker Compose |

## Architecture

Modular monolith — one Spring Boot application with feature packages:

`auth` · `users` · `players` · `teams` · `sports` · `tournaments` · `matches` · `referees` · `statistics` · `notifications` · `admin`

See [docs/architecture.md](docs/architecture.md), [docs/database.md](docs/database.md), and [docs/api.md](docs/api.md).

## Roles

| Role | Notes |
|---|---|
| `FAN` | Default registration role |
| `PLAYER` | Has a player profile |
| `CAPTAIN` | Manages own team roster |
| `REFEREE` | Controls assigned matches (orthogonal) |
| `ADMIN` | Tournaments, approvals, referee assignment (orthogonal) |

Hierarchy: `FAN` < `PLAYER` < `CAPTAIN`. Authorization is always enforced on the backend.

## Quick start (development)

```bash
cp .env.example .env
docker compose up -d postgres redis
cd backend && ./mvnw spring-boot:run
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
Health: `http://localhost:8080/api/v1/health`

Full stack (when wired):

```bash
docker compose up --build
```

## Environment variables

Copy `.env.example` to `.env`. Never commit secrets.

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `REDIS_URL` | Redis connection |
| `JWT_SECRET` | HMAC secret for access tokens |
| `JWT_ACCESS_EXPIRATION` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | Object storage |
| `CORS_ORIGINS` | Allowed browser origins |

## Project layout

```
├── backend/          Spring Boot API
├── web/              Vue 3 application (Phase 7)
├── mobile/           Flutter application (Phase 8)
├── docs/             Architecture, database, API
├── docker-compose.yml
└── .env.example
```

## Development phases

1. Architecture + database + authentication ← **current**
2. Users + players + teams
3. Tournaments + matches
4. Referee mode + match events
5. WebSocket + live score
6. Statistics
7. Vue web
8. Flutter mobile
9. Push notifications
10. Testing + Docker + production hardening

## License

Proprietary — student league project.
