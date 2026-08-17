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

## Как получить релиз прямо сейчас

Релизный workflow **не** запускается на обычный push в ветку. Нужен тег:

```bash
# 1) дождаться зелёного CI на ветке/PR
# 2) создать и запушить тег (из ветки с готовым кодом или после merge в main)
git checkout cursor/bc-a9909673-7bed-451d-be9b-396d953591e8-52db   # или main после merge
git pull
git tag v0.1.0
git push origin v0.1.0
```

Либо вручную: GitHub → **Actions** → **Release** → **Run workflow** → указать версию (например `0.1.0`).

Для первого релиза **никакие secrets не обязательны**:
- Android APK/AAB соберутся на default keystore
- iOS — unsigned zip (без Apple-сертификатов), job не блокирует релиз если упадёт

Secrets (`ANDROID_KEYSTORE_*`, Apple certs) нужны только для публикации в Google Play / App Store.

## Пример релизного потока

```bash
# убедиться, что CI на ветке/main зелёный
git tag v0.1.0
git push origin v0.1.0
# GitHub Actions → Release загружает ассеты в GitHub Release
```
