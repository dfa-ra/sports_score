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

See [docs/architecture.md](docs/architecture.md), [docs/database.md](docs/database.md), [docs/api.md](docs/api.md), and [docs/ci.md](docs/ci.md).

## CI / Releases

GitHub Actions:

- **CI** (`.github/workflows/ci.yml`) — backend tests, Vue production build, Flutter analyze/test + debug APK
- **Release** (`.github/workflows/release.yml`) — on `v*` tags (or manual dispatch): backend JAR, web tarball, Android APK/AAB, optional unsigned iOS zip → GitHub Release assets

```bash
git tag v0.1.0
git push origin v0.1.0
```

Details and signing notes: [docs/ci.md](docs/ci.md).

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
docker compose up -d postgres redis minio minio-init
cd backend && ./mvnw spring-boot:run
cd web && npm install && npm run dev
```

Full stack (API + dependencies, including MinIO):

```bash
cp .env.example .env
docker compose up --build
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
Health: `http://localhost:8080/api/v1/health`  
MinIO console: `http://localhost:9001` (minioadmin / minioadmin)

## Production checklist

- Set a strong unique `JWT_SECRET` (≥ 32 chars; never the example value)
- Override `DATABASE_PASSWORD` (prod profile rejects the default)
- Use `SPRING_PROFILES_ACTIVE=prod` (`ddl-auto=validate`, Flyway on)
- Configure real `S3_*` credentials (or keep `S3_ENABLED=false` with no-op storage)
- Restrict `CORS_ORIGINS` to real front-end origins
- Enable FCM/APNs providers only with real credentials (`app.push.fcm.enabled` / `app.push.apns.enabled`)
- Prefer Redis (`APP_REDIS_ENABLED=true`) for multi-instance rate limiting / live fan-out

## Environment variables

Copy `.env.example` to `.env`. Never commit secrets.

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `REDIS_URL` | Redis connection |
| `APP_REDIS_ENABLED` | Enable Redis auto-config / cluster features |
| `JWT_SECRET` | HMAC secret for access tokens |
| `JWT_ACCESS_EXPIRATION` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) |
| `S3_ENABLED` | Use S3-compatible storage instead of no-op |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | Object storage |
| `S3_PUBLIC_BASE_URL` | Public URL prefix for stored objects |
| `CORS_ORIGINS` | Allowed browser origins |
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` |

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

1. Architecture + database + authentication ✅
2. Users + players + teams ✅
3. Tournaments + matches ✅
4. Referee mode + match events ✅
5. WebSocket + live score ✅
6. Statistics ✅
7. Vue web ✅
8. Flutter mobile ✅ (skeleton + referee large-button UI)
9. Push notifications ✅ (provider abstraction + no-op)
10. Testing + Docker + production hardening ✅ (baseline)

## Web

```bash
cd web && npm install && npm run dev
```

## Mobile

See [mobile/README.md](mobile/README.md).

## License

Proprietary — student league project.
