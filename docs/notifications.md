# Notifications

Push notifications are delivered through `NotificationService` → `PushNotificationProvider`.

Default provider: `NoOpPushNotificationProvider` (logs only).

Planned providers:

- Firebase Cloud Messaging (Android / web)
- Apple Push Notification Service (iOS)

Domain events:

- match starting / finished
- goal
- tournament registration
- team invitation
- schedule update

Enable a real provider by adding an implementation annotated with `@Component` and configuring credentials via environment variables (never commit secrets).
