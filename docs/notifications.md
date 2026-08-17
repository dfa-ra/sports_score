# Уведомления

Push-уведомления доставляются через цепочку `NotificationService` → `PushNotificationProvider`.

Провайдер по умолчанию: `NoOpPushNotificationProvider` (только логирует).

Опциональные stubs (включаются конфигом):

- `app.push.fcm.enabled=true` → `FcmPushNotificationProvider`
- `app.push.apns.enabled=true` → `ApnsPushNotificationProvider`

## Регистрация клиента

Авторизованные клиенты регистрируют device tokens:

```http
POST /api/v1/notifications/device-tokens
{ "platform": "ANDROID", "token": "..." }
```

Платформы: `ANDROID`, `IOS`, `WEB`. Токены хранятся в `device_tokens` (миграция Flyway `V3`).

## Доменные события (подключены)

| Событие | Триггер |
|---|---|
| `MATCH_STARTING` | Судья стартует матч |
| `MATCH_FINISHED` | Судья завершает матч |
| `GOAL` | Судья фиксирует событие GOAL |
| `TOURNAMENT_REGISTRATION` | Капитан подаёт заявку команды |
| `TEAM_INVITATION` | Капитан/admin добавляет игрока в состав |
| `SCHEDULE_UPDATE` | Матч запланирован / судья назначен |

Публикация идёт через `@Async`, чтобы доменные запросы не блокировались провайдерами.

Реальный провайдер подключается реализацией `PushNotificationProvider` и credentials из переменных окружения (секреты в git не коммитить).
