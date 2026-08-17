# CI / CD

Workflows GitHub Actions лежат в `.github/workflows/`.

## Непрерывная интеграция (`ci.yml`)

Запускается на push и pull request:

| Job | Что делает |
|---|---|
| **Backend** | Java 21 + `./mvnw test` и `package` |
| **Web** | Node 22 + `npm ci` + `npm run build` (артефакт `web/dist`) |
| **Mobile** | Flutter stable: `analyze`, `test`, smoke debug APK |

## Релизы (`release.yml`)

Триггеры:

- push тега версии: `git tag v0.2.0 && git push origin v0.2.0`
- или **Actions → Release → Run workflow** (ручной ввод версии)

Создаёт GitHub Release с артефактами:

| Артефакт | Примечание |
|---|---|
| Backend JAR | Spring Boot fat JAR |
| Web `student-league-web-<ver>.tar.gz` | Production `dist/` Vite |
| Android APK + AAB | Flutter `--release` (пока default keystore, пока не добавлены secrets подписи) |
| iOS unsigned zip | `Runner.app` с `macos-latest` (`--no-codesign`); опционально / не блокирует релиз |

### Подпись Android (готово к Store)

Добавьте secrets репозитория и расширьте job `android-release`:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

В CI нужно декодировать keystore и передать `key.properties` / Gradle properties перед `flutter build appbundle --release`.

### Подпись iOS (App Store)

Нужны сертификаты Apple, provisioning profiles и желательно [Fastlane Match](https://docs.fastlane.tools/actions/match/). Текущий job только проверяет, что iOS-таргет собирается без codesign.

## Пример релизного потока

```bash
# убедиться, что CI на main зелёный
git tag v0.1.0
git push origin v0.1.0
# GitHub Actions → Release загружает ассеты в GitHub Release
```
