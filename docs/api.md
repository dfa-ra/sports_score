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
| POST | `/auth/google` | Публичный | Вход по Google ID token, те же access/refresh |
| POST | `/auth/forgot-password` | Публичный | Письмо со ссылкой сброса. Ответ одинаковый, есть аккаунт или нет |
| POST | `/auth/change-password-email` | Bearer | Та же ссылка на почту текущего аккаунта. Тело не нужно. Всегда `204` |
| POST | `/auth/reset-password` | Публичный | Новый пароль по одноразовой ссылке |
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

На register/login/google/forgot-password/change-password-email/reset-password/refresh действует rate limiting.

### Google

`POST /auth/google`

```json
{ "idToken": "<Google ID token>" }
```

Ответ `200` — тот же объект, что у login.

Backend проверяет подпись токена, срок, issuer Google и audience из `GOOGLE_CLIENT_ID` (несколько id через запятую, если у веба и мобильного клиента разные). Почта должна быть подтверждена Google (`email_verified`). Пустой `GOOGLE_CLIENT_ID` — `503`, вход по паролю при этом работает.

- subject уже привязан — вход в этот аккаунт, email другого пользователя не забирается;
- email уже есть, Google ещё не привязан — вход и запись `google_sub`;
- email есть и привязан к другому subject — `409`;
- пользователя нет — создаётся **FAN** без пароля. PLAYER / CAPTAIN / REFEREE так не выдаются (для них по-прежнему регистрация с фото).

Кнопка на вебе берёт публичный client id из `VITE_GOOGLE_CLIENT_ID` на этапе сборки. Пустое значение прячет кнопку. Сборки CI и стендов подставляют id из `deploy/google-client-id`, если переменная GitHub пустая.

### Восстановление пароля

`POST /auth/forgot-password` — `{ "email" }` → всегда `204`, и если почты нет в базе тоже. В письме ссылка `{APP_PUBLIC_URL}/reset-password?token=...` (1 час, один раз). В базе хранится только SHA-256, сырой токен в логи не пишется.

`POST /auth/change-password-email` — без тела, только Bearer текущего пользователя. Почта берётся из аккаунта, в теле её передавать не нужно. Та же таблица токенов и та же страница сброса. Всегда `204`: и если у аккаунта нет почты, и если SMTP не настроен. Чужой email в теле игнорируется.

`POST /auth/reset-password` — `{ "token", "password" }`. Пароль от 8 до 100 символов, как при регистрации. Успех — `204`. Старые refresh-токены отзываются. Повтор той же ссылки — `400`.

Аккаунт только с Google (`password_hash` пустой) может задать пароль этой ссылкой и дальше входить и через Google, и по почте. Пока `MAIL_HOST` или `MAIL_FROM` пустые, письмо не уходит (в лог пишется факт, без ссылки) — для локальной разработки и тестов.

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
| GET | `/players` | Публичный. Без `q` длиннее 1 символа список пустой (подсказки поиска). `teamId` отдаёт состав команды. Размер страницы не больше 12 |
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
| DELETE | `/teams/{id}` | ADMIN — расформировать (состав снимается, заявки на турниры — WITHDRAWN). `?purge=true` — удалить уже расформированную команду, если у неё нет матчей |
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
| DELETE | `/tournaments/{id}/teams/{teamId}` | ADMIN — убрать из турнира полностью: заявка удаляется, несыгранные матчи этой команды в турнире удаляются. Сыгранные матчи остаются в календаре и в таблицу больше не идут |
| GET | `/tournaments/{id}/tables` | Публичный — группы турнира и команды в каждой |
| PUT | `/tournaments/{id}/tables` | ADMIN — заменить набор таблиц: `{ tables: [{ name, teamIds }] }`. Пустой список снимает группы |
| GET | `/tournaments/{id}/standings` | Публичный. `{ tables: [{ id, name, sortOrder, rows }] }`. Без групп — одна общая таблица |
| GET | `/tournaments/{id}/matches` | Auth |

---

## Матчи

| Метод | Путь | Auth |
|---|---|---|
| GET | `/matches` | Публичный — фильтры |
| GET | `/matches/{id}` | Публичный. В ответе: `period`, `periodCount`, `periodLengthSeconds` (по умолчанию 2×20 мин), `clockRunningSince`, `sportCode` |
| POST | `/matches` | ADMIN. Опционально `periodCount` (1–8), `periodLengthMinutes` (1–90, иначе 2×20) и `venue` (зал, до 120 символов) |
| PUT | `/matches/{id}` | ADMIN. Опционально `venue` (зал, до 120 символов); пустая строка очищает |
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
| GET | `/statistics/players` | Публичный — фильтры. События матчей читаются одним запросом, не по матчу |
| GET | `/statistics/board` | Публичный — `{ scorers, assists, goalkeepers }` за один проход. `tournamentId`, `limit` (до 100) |
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
