# Student League Mobile

Flutter feature-based client for Android and iOS.

```
lib/
  core/       # networking, theme, app shell
  features/   # auth, home, tournaments, matches, teams, players, statistics, profile, referee
  shared/     # shared widgets/models
```

## Local

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

Referee mode uses large action buttons for live event entry.

## CI builds

GitHub Actions builds:

- debug APK on every CI run
- release APK + AAB (and optional unsigned iOS payload) on version tags — see [docs/ci.md](../docs/ci.md)
