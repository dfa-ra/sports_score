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
# стенд по умолчанию: http://144.31.153.52:3000/api/v1
flutter run
# локальный backend
flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080/api/v1
```

Релизный APK ходит на стенд через nginx (`:3000/api/v1`). Порт `8080` в Docker не проброшен наружу — это нормально. Сменить адрес в профиле может только админ.

Режим судьи использует крупные кнопки для быстрого ввода событий во время матча.

## CI-сборки

GitHub Actions собирает:

- debug APK на каждом CI-прогоне
- release APK + AAB (и опциональный unsigned iOS payload) по version tags — см. [docs/ci.md](../docs/ci.md)
