# Student League — Mobile

Flutter-клиент (одна codebase для Android и iOS) с feature-based архитектурой.

```
lib/
  core/       # сеть, тема, оболочка приложения
  features/   # auth, home, tournaments, matches, teams, players, statistics, profile, referee
  shared/     # общие виджеты/модели
```

## Локально

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

Режим судьи использует крупные кнопки для быстрого ввода событий во время матча.

## CI-сборки

GitHub Actions собирает:

- debug APK на каждом CI-прогоне
- release APK + AAB (и опциональный unsigned iOS payload) по version tags — см. [docs/ci.md](../docs/ci.md)
