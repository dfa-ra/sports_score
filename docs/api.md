# API

Базовый URL: `/api/v1`  
Content-Type: `application/json`  
Auth: `Authorization: Bearer <access_token>`

Интерактивный Swagger/OpenAPI **закрыт** для пользователей (`/swagger-ui.html`, `/v3/api-docs`, `/actuator/**`). Публичная поверхность — Vue-приложение; этот файл — внутренняя спецификация для разработки.

## Соглашения

### Пагинация

Списочные endpoints принимают:

- `page` (с нуля), `size`, `sort` (напр. `scheduledAt,desc`)

Обёртка ответа:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Ошибки

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Краткое человекочитаемое описание",
  "details": [{ "field": "email", "message": "must be a well-formed email address" }],
  "timestamp": "2026-08-17T12:00:00Z",
  "path": "/api/v1/auth/register"
}
```

Частые коды: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `BUSINESS_RULE_VIOLATION`.

### Фильтрация

Где применимо: query-параметры `tournamentId`, `status`, `teamId`, `playerId`, `seasonYear`, `sportCode`, `from`, `to`.

---

## Аутентификация

| Метод | Путь | Auth | Описание |
|---|---|---|---|
| POST | `/auth/register` | Публичный | Создать аккаунт FAN |
| POST | `/auth/login` | Публичный | Access + refresh токены |
| POST | `/auth/refresh` | Публичный (refresh в body) | Ротация refresh, новый access |
| POST | `/auth/logout` | Bearer или refresh | Отозвать refresh |
| GET | `/auth/me` | Bearer | Текущий пользователь |

### Регистрация

Запрос:

```json
{
  "email": "fan@example.com",
  "password": "Str0ngPass!",
  "accountType": "FAN"
}
```

Или игрок:

```json
{
  "email": "player@example.com",
  "password": "Str0ngPass!",
  "accountType": "PLAYER",
  "firstName": "Иван",
  "lastName": "Иванов"
}
```

`accountType`: только `FAN` (зритель) или `PLAYER` (игрок).  
Админ **не** регистрируется через API — задаётся в `.env` (`ADMIN_EMAIL` / `ADMIN_PASSWORD`).

Ответ `201`: краткая карточка пользователя (id, email, role) — **без пароля**.

### Login

Запрос: `{ "email", "password" }`  

Ответ `200`:

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

Запрос: `{ "refreshToken": "..." }` → новый access + refresh (старый refresh отозван).

На register/login/refresh действует rate limiting.

---

## Health

| Метод | Путь | Auth |
|---|---|---|
| GET | `/health` | Публичный |

---

## Пользователи (admin)

| Метод | Путь | Роли |
|---|---|---|
| GET | `/admin/users` | ADMIN |
| PATCH | `/admin/users/{id}` | ADMIN — enabled/role |

---

## Игроки

| Метод | Путь | Auth |
|---|---|---|
| GET | `/players` | Публичный |
| GET | `/players/{id}` | Публичный |
| GET | `/players/{id}/card` | Публичный |
| PUT | `/players/me` | Свой профиль (создаёт/обновляет) |

Публичная карточка: имя, фото, команда, номер, позиция, статистика, история матчей.

---

## Команды

| Метод | Путь | Auth |
|---|---|---|
| GET/POST | `/teams` | Чтение: публичное (без расформированных; `includeDisbanded=true` — все). Создание: игрок/капитан (становится капитаном). ADMIN создавать не может |
| GET/PUT | `/teams/{id}` | Изменение: капитан команды или ADMIN. Расформированную править нельзя |
| DELETE | `/teams/{id}` | ADMIN — расформировать (состав снимается, заявки на турниры — WITHDRAWN) |
| GET | `/teams/{id}/members` | Публичный |
| POST | `/teams/{id}/members` | Капитан команды или ADMIN |
| DELETE | `/teams/{id}/members/{playerId}` | Капитан команды или ADMIN |
| PUT | `/teams/{id}/captain` | Капитан или ADMIN |

---

## Виды спорта

| Метод | Путь | Auth |
|---|---|---|
| GET | `/sports` | Публичный |

---

## Турниры

| Метод | Путь | Auth |
|---|---|---|
| GET/POST | `/tournaments` | GET: публичный; POST: ADMIN |
| GET/PUT | `/tournaments/{id}` | PUT: ADMIN |
| POST | `/tournaments/{id}/teams` | CAPTAIN — заявка своей команды |
| POST | `/tournaments/{id}/teams/{teamId}/approve` | ADMIN |
| DELETE | `/tournaments/{id}/teams/{teamId}` | ADMIN — исключение |
| GET | `/tournaments/{id}/standings` | Auth |
| GET | `/tournaments/{id}/matches` | Auth |

---

## Матчи

| Метод | Путь | Auth |
|---|---|---|
| GET | `/matches` | Публичный — фильтры |
| GET | `/matches/{id}` | Публичный. В ответе: `period`, `periodCount`, `periodLengthSeconds` (по умолчанию 2×20 мин), `clockRunningSince`, `sportCode` |
| POST | `/matches` | ADMIN. Опционально `periodCount` (1–8) и `periodLengthMinutes` (1–90), иначе 2×20 |
| POST | `/matches/{id}/referees` | ADMIN |
| GET | `/matches/{id}/events` | Публичный. Имена игроков, `period`, `secondaryPlayer*` = пас / кто вышел |
| GET | `/matches/{id}/referees` | Публичный |
| GET | `/matches/{id}/lineups` | Публичный — основа и скамейка; если капитан не записал, вся заявка как скамейка |
| PUT | `/matches/{id}/lineups` | Капитан этой команды, назначенный судья или ADMIN |

---

## Судья / события матча

Все действия судьи требуют, чтобы `currentUser` был назначен на матч.

| Метод | Путь | Auth |
|---|---|---|
| GET | `/referee/matches` | REFEREE |
| POST | `/referee/matches/{id}/start` | Назначенный судья |
| POST | `/referee/matches/{id}/pause` | Назначенный судья |
| POST | `/referee/matches/{id}/resume` | Назначенный судья |
| POST | `/referee/matches/{id}/finish` | Назначенный судья |
| POST | `/referee/matches/{id}/next-period` | Назначенный судья — следующий тайм, часы с нуля |
| POST | `/referee/matches/{id}/events` | Назначенный судья. Для гола/карточки/замены нужен `playerId`; голевая — `secondaryPlayerId` |
| POST | `/referee/matches/{id}/events/{eventId}/void` | Назначенный судья |

---

## Статистика

| Метод | Путь | Auth |
|---|---|---|
| GET | `/statistics/players` | Публичный — фильтры |
| GET | `/statistics/teams` | Публичный — фильтры |

Считается из не-voided записей `MatchEvent`.

---

## WebSocket

- Connect: `ws://host/ws` (STOMP).
- Subscribe: `/topic/matches/{matchId}`.
- Auth: JWT на CONNECT.

Пример сообщения:

```json
{
  "type": "MATCH_UPDATE",
  "matchId": "...",
  "status": "LIVE",
  "homeScore": 1,
  "awayScore": 0,
  "gameTimeSeconds": 320,
  "period": 1,
  "periodCount": 2,
  "periodLengthSeconds": 1200,
  "clockRunningSince": "2026-08-17T16:00:00Z",
  "sportCode": "FOOTBALL",
  "lastEvent": { "eventType": "GOAL", "playerId": "...", "playerName": "Иванов", "teamId": "..." }
}
```

---

## Уведомления / Push

| Метод | Путь | Auth | Описание |
|---|---|---|---|
| POST | `/notifications/device-tokens` | Bearer | Зарегистрировать ANDROID/IOS/WEB token |
| DELETE | `/notifications/device-tokens?token=` | Bearer | Удалить token |

Доставка: `NotificationService` → `PushNotificationProvider` (по умолчанию no-op; опциональные stubs FCM/APNs).

Доменные триггеры: старт/финиш матча, гол, заявка на турнир, приглашение в команду, обновление расписания.

---

## Загрузки файлов

| Метод | Путь | Auth |
|---|---|---|
| POST | `/uploads/players/me/avatar` | Авторизованный (multipart `file`) |
| POST | `/uploads/teams/{teamId}/logo` | Капитан команды или ADMIN |

---

## Матрица авторизации (кратко)

Чтение каталога (турниры, матчи, команды, игроки, статистика, виды спорта) **публичное**: регистрация не нужна.

| Возможность | Без входа | FAN | PLAYER | CAPTAIN | REFEREE | ADMIN |
|---|---|---|---|---|---|---|
| Просмотр публичных данных | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Свой профиль | | ✓ | ✓ | | ✓ |
| Состав своей команды | | | ✓* | | ✓ |
| Заявка команды на турнир | | | ✓* | | ✓ |
| Создание/редактирование турниров | | | | | ✓ |
| Контроль назначенного матча | | | | ✓* | |
| Назначение судей | | | | | ✓ |

\* Обязательны проверки ownership (капитан именно этой команды / назначенный судья).
