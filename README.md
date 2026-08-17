# Student League

Production-ориентированная платформа студенческой спортивной лиги: live-счёт, расписания, статистика и управление командами, игроками, судьями и турнирами.

По просмотру матчей близка к FlashScore, плюс операционные возможности для капитанов, судей и администраторов.

## Стек

| Слой | Технологии |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, JPA/Hibernate, Flyway |
| БД | PostgreSQL (источник истины) |
| Кэш / Pub-Sub | Redis |
| API | REST `/api/v1`, OpenAPI/Swagger, WebSocket (STOMP) |
| Web | Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios |
| Mobile | Flutter (Android + iOS) |
| Хранилище | S3-совместимое object storage (аватары, логотипы) |
| Инфраструктура | Docker Compose |

## Архитектура

Модульный монолит — одно Spring Boot-приложение с пакетами по доменам:

`auth` · `users` · `players` · `teams` · `sports` · `tournaments` · `matches` · `referees` · `statistics` · `notifications` · `admin`

См. [docs/architecture.md](docs/architecture.md), [docs/database.md](docs/database.md), [docs/api.md](docs/api.md) и [docs/ci.md](docs/ci.md).

## CI / Релизы

GitHub Actions:

- **CI** (`.github/workflows/ci.yml`) — тесты backend, production-сборка Vue, Flutter analyze/test + debug APK
- **Release** (`.github/workflows/release.yml`) — по тегам `v*` (или ручной запуск): JAR backend, tarball web, Android APK/AAB, опциональный unsigned iOS zip → артефакты GitHub Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Важно: обычный push в ветку запускает только **CI**, не Release. Релизные APK/AAB/JAR появляются после тега `v*` или ручного Run workflow в Actions.

Подробности и подпись билдов: [docs/ci.md](docs/ci.md).

## Роли

| Роль | Описание |
|---|---|
| `FAN` | Роль по умолчанию при регистрации |
| `PLAYER` | Есть профиль игрока |
| `CAPTAIN` | Управляет составом своей команды |
| `REFEREE` | Контролирует назначенные матчи (отдельная роль) |
| `ADMIN` | Турниры, подтверждения, назначение судей (отдельная роль) |

Иерархия: `FAN` < `PLAYER` < `CAPTAIN`. Авторизация всегда проверяется на backend.

## Быстрый старт (разработка)

```bash
cp .env.example .env
docker compose up -d postgres redis minio minio-init
cd backend && ./mvnw spring-boot:run
cd web && npm install && npm run dev
```

Полный стек (API + зависимости, включая MinIO):

```bash
cp .env.example .env
docker compose up --build
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
Health: `http://localhost:8080/api/v1/health`  
Консоль MinIO: `http://localhost:9001` (minioadmin / minioadmin)

## Чеклист для production

- Задать уникальный сильный `JWT_SECRET` (≥ 32 символов; не значение из примера)
- Переопределить `DATABASE_PASSWORD` (prod-профиль отклоняет дефолт)
- Использовать `SPRING_PROFILES_ACTIVE=prod` (`ddl-auto=validate`, Flyway включён)
- Настроить реальные `S3_*` (или оставить `S3_ENABLED=false` с no-op хранилищем)
- Ограничить `CORS_ORIGINS` реальными origin фронтенда
- Включать FCM/APNs только с реальными credentials (`app.push.fcm.enabled` / `app.push.apns.enabled`)
- Для нескольких инстансов предпочтителен Redis (`APP_REDIS_ENABLED=true`)

## Переменные окружения

Скопируйте `.env.example` в `.env`. Секреты в git не коммитить.

| Переменная | Назначение |
|---|---|
| `DATABASE_URL` | JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Учётные данные БД |
| `REDIS_URL` | Подключение к Redis |
| `APP_REDIS_ENABLED` | Включить Redis auto-config / кластерные фичи |
| `JWT_SECRET` | HMAC-секрет access-токенов |
| `JWT_ACCESS_EXPIRATION` | TTL access-токена (мс) |
| `JWT_REFRESH_EXPIRATION` | TTL refresh-токена (мс) |
| `S3_ENABLED` | Использовать S3 вместо no-op |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | Object storage |
| `S3_PUBLIC_BASE_URL` | Публичный префикс URL объектов |
| `CORS_ORIGINS` | Разрешённые browser origins |
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` |

## Структура репозитория

```
├── backend/          Spring Boot API
├── web/              Vue 3 приложение
├── mobile/           Flutter приложение
├── docs/             Архитектура, БД, API, CI
├── .github/workflows CI и релизы
├── docker-compose.yml
└── .env.example
```

## Фазы разработки

1. Архитектура + БД + аутентификация ✅
2. Пользователи + игроки + команды ✅
3. Турниры + матчи ✅
4. Режим судьи + события матча ✅
5. WebSocket + live-счёт ✅
6. Статистика ✅
7. Vue web ✅
8. Flutter mobile ✅ (скелет + UI судьи с крупными кнопками)
9. Push-уведомления ✅ (абстракция провайдера + no-op)
10. Тесты + Docker + production hardening ✅

## Web

```bash
cd web && npm install && npm run dev
```

## Mobile

См. [mobile/README.md](mobile/README.md).

## Лицензия

Proprietary — проект студенческой лиги.
