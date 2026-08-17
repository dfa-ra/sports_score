# Архитектура

## Обзор

Student League — **модульный монолит**: одно приложение Spring Boot 3, разделённое на feature-пакеты. Доменную логику можно позже выделить в сервисы без переписывания бизнес-правил.

Клиенты:

- **Web** — SPA на Vue 3
- **Mobile** — Flutter (одна codebase для Android/iOS)
- **Интеграции** — REST `/api/v1` (Swagger/OpenAPI UI в runtime не публикуется)

## Схема верхнего уровня

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
   PostgreSQL    Redis    Диск /media
   (истина)    (кэш/      (медиа)
               pubsub/
               rate limit)
```

## Границы модулей

| Модуль | Ответственность |
|---|---|
| `common` | Ошибки, пагинация, общие типы |
| `config` | Security, Redis, WebSocket, CORS, Jackson |
| `security` | JWT, principals, фильтры, helpers прав |
| `auth` | Регистрация, login, logout, refresh |
| `users` | Администрирование аккаунтов |
| `players` | Профили игроков и публичные карточки |
| `teams` | Команды, состав, капитанство |
| `sports` | Каталог видов спорта (FOOTBALL, …) |
| `tournaments` | Турниры, заявки команд |
| `matches` | Матчи, события, live-контроль, score policies |
| `referees` | Представления назначений судей |
| `statistics` | Агрегация из `MatchEvent` |
| `notifications` | Абстракция push (FCM/APNs) |
| `storage` | Локальные файлы на диске (`/media`) |
| `admin` | Сквозные admin API |

Каждый feature-пакет использует слои: `controller` → `service` → `repository`, плюс `entity`, `dto`, `mapper`.

## Правила слоёв

1. Controllers принимают/возвращают только DTO — никогда JPA entities.
2. Бизнес-правила живут в services (транзакции, проверки ownership).
3. Repositories — Spring Data интерфейсы; избегать огромных entity graphs.
4. Межмодульные вызовы — через services (или небольшие ports), а не через чужие repositories, когда это возможно.

## Аутентификация и авторизация

- **Access token:** JWT (короткоживущий), заголовок Bearer.
- **Refresh token:** непрозрачное случайное значение; в БД хранится только SHA-256; при каждом refresh выполняется **ротация**.
- **Пароли:** BCrypt; `passwordHash` никогда не отдаётся в API.
- **Роли:** `FAN` < `PLAYER` < `CAPTAIN` через Spring `RoleHierarchy`. `REFEREE` и `ADMIN` — ортогональные роли.
- **Ownership:** капитан проверяется по `Team.captainId` vs `PlayerProfile` текущего пользователя; судья — по назначению `MatchReferee`. Role/ids с клиента для авторизации не доверяются.

## События матча и live-счёт

`MatchEvent` — **источник истины** для счёта и статистики.

После действия судьи:

1. Проверка назначения судьи + state machine матча + правила вида спорта.
2. Сохранение `MatchEvent` в PostgreSQL.
3. Пересчёт счёта/статуса через sport-specific `ScorePolicy`.
4. Публикация в Redis Pub/Sub (`studentleague:match-live` при включённом Redis).
5. Fan-out через WebSocket на STOMP-топик `/topic/matches/{matchId}`.

Клиенты подписываются один раз; polling для live не нужен.

## WebSocket

- Endpoint: `/ws` (STOMP поверх SockJS/WebSocket).
- Subscribe: `/topic/matches/{matchId}`.
- Auth на CONNECT: JWT (query или header interceptor).
- Payload: изменение счёта, новые/отменённые события, статус, игровое время, завершение матча.

## Использование Redis

| Назначение | Примечание |
|---|---|
| Кэш | Тяжёлые на чтение списки/статы с TTL (опционально позже) |
| Pub/Sub | Live-распространение событий при `APP_REDIS_ENABLED=true` |
| Rate limiting | Auth endpoints (по умолчанию in-memory; готовность к Redis) |
| Временные данные | Короткоживущие locks / ephemeral state |

Включение live fan-out: `APP_REDIS_ENABLED=true`.

1. Persist события/состояния в PostgreSQL  
2. `LiveMatchPublisher` публикует JSON в канал Redis `studentleague:match-live`  
3. Каждый инстанс backend слушает канал и рассылает в локальный STOMP `/topic/matches/{matchId}`

Если Redis выключен, обновления публикуются напрямую в локальный STOMP broker (один инстанс).

PostgreSQL остаётся единственным durable source of truth.

## Файловое хранилище

Аватары и логотипы сохраняются **на диск сервера** (`LOCAL_STORAGE_DIR`, в Docker — `/app/data/uploads`) и отдаются по URL `/media/...`.

## Уведомления

Интерфейсы `NotificationService` / `PushNotificationProvider` позволяют менять FCM, APNs или no-op. Доменные сервисы эмитят intents; провайдеры доставляют асинхронно.

## Деплой

Local/dev: Docker Compose (`backend`, `postgres`, `redis`).

Production: те же контейнеры за reverse proxy; секреты через env; Flyway при старте; `ddl-auto=validate` (никогда `create`).

## Будущее выделение сервисов

Feature-пакеты соответствуют кандидатам в сервисы 1:1. Общий kernel (`common`, auth-контракты) станет библиотеками; Redis/WS уже рассчитаны на multi-instance fan-out.
