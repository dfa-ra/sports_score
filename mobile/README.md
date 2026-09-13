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
# прод по умолчанию: https://itmoliga.ru/api/v1
flutter run
# закрытый дев-стенд
flutter run --dart-define=API_BASE_URL=http://144.31.153.52:3000/api/v1
# локальный backend
flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080/api/v1
```

Релизный APK/AAB ходит на прод (`https://itmoliga.ru/api/v1`, VPS `212.113.109.203`). HTTP по голому IP не использовать: снаружи `:80` отвечает чужой прокси (502). Закрытый тестовый APK (`student-league-dev-android.apk`) лежит в GitHub prerelease `dev` и смотрит на `http://144.31.153.52:3000/api/v1`. Сменить адрес в профиле может только админ.

Режим судьи использует крупные кнопки для быстрого ввода событий во время матча.

## CI-сборки

GitHub Actions собирает:

- debug APK на каждом CI-прогоне
- release APK + AAB (и опциональный unsigned iOS payload) по version tags — см. [docs/ci.md](../docs/ci.md)
