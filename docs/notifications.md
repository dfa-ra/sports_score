# Notifications

Push notifications are delivered through `NotificationService` → `PushNotificationProvider`.

Default provider: `NoOpPushNotificationProvider` (logs only).

Optional stubs (enable via config):

- `app.push.fcm.enabled=true` → `FcmPushNotificationProvider`
- `app.push.apns.enabled=true` → `ApnsPushNotificationProvider`

## Client registration

Authenticated clients register device tokens:

```http
POST /api/v1/notifications/device-tokens
{ "platform": "ANDROID", "token": "..." }
```

Platforms: `ANDROID`, `IOS`, `WEB`. Tokens are stored in `device_tokens` (Flyway `V3`).

## Domain events (wired)

| Event | Trigger |
|---|---|
| `MATCH_STARTING` | Referee starts match |
| `MATCH_FINISHED` | Referee finishes match |
| `GOAL` | Referee records GOAL event |
| `TOURNAMENT_REGISTRATION` | Captain registers team |
| `TEAM_INVITATION` | Captain/admin adds player to roster |
| `SCHEDULE_UPDATE` | Match scheduled / referee assigned |

Publishing is `@Async` so domain requests are not blocked by providers.

Enable a real provider by implementing `PushNotificationProvider` and configuring credentials via environment variables (never commit secrets).
