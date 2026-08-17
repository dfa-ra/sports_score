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
| GET/POST | `/teams` | Чтение: публичное; создание: игрок (становится капитаном) или ADMIN |
| GET/PUT | `/teams/{id}` | Изменение: капитан команды или ADMIN |
| GET | `/teams/{id}/members` | Auth |
| POST | `/teams/{id}/members` | Капитан команды |
| DELETE | `/teams/{id}/members/{playerId}` | Капитан команды |
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
| GET | `/matches/{id}` | Публичный |
| POST | `/matches` | ADMIN |
| POST | `/matches/{id}/referees` | ADMIN |
| GET | `/matches/{id}/events` | Auth |
| GET | `/matches/{id}/referees` | Auth |

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
| POST | `/referee/matches/{id}/events` | Назначенный судья |
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
  "lastEvent": { "eventType": "GOAL", "playerId": "...", "teamId": "..." }
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
