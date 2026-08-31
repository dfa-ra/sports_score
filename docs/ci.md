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
| `student-league-backend-<ver>.jar` | Spring Boot fat JAR |
| `student-league-web-<ver>.tar.gz` | Production `dist/` Vite |
| Android APK + AAB | Flutter `--release` с `--dart-define=API_BASE_URL=http://144.31.153.52:3000/api/v1` (пока default keystore) |
| iOS unsigned zip | `Runner.app` с `macos-latest` (`--no-codesign`); опционально / не блокирует релиз |

После публикации релиза `v*` workflow **Dev stand** сам качает эти ассеты на сервер и поднимает контейнеры.

### Подпись Android (готово к Store)

Добавьте secrets репозитория и расширьте job `android-release`:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

В CI нужно декодировать keystore и передать `key.properties` / Gradle properties перед `flutter build appbundle --release`.

### Подпись iOS (App Store)

Нужны сертификаты Apple, provisioning profiles и желательно [Fastlane Match](https://docs.fastlane.tools/actions/match/). Текущий job только проверяет, что iOS-таргет собирается без codesign.

## Автодеплой на dev-стенд (`stand.yml`)

Полностью автоматический контур:

1. CI собирает backend JAR + web tarball и публикует их в GitHub Release.
2. По SSH на сервер копируется папка `deploy/` (compose + Dockerfile’ы под **готовые** ассеты, не сборка из исходников).
3. На сервере `deploy/stand.sh` скачивает релиз через GitHub API и делает `docker compose up -d --build`.
4. Проверяется `http://127.0.0.1:$WEB_PORT/api/v1/health`.

Триггеры:

| Событие | Что выкатывается |
|---|---|
| Push в `main` | Rolling prerelease с тегом `dev` (без мобильных сборок) → сразу деплой |
| GitHub Release `v*` | Тот же стенд переключается на версию из релиза |
| **Actions → Dev stand → Run workflow** | Пустой tag = пересобрать `dev`; либо указать уже существующий tag (`dev`, `v0.2.0`) |

Если SSH-секреты ещё не заданы, job деплоя **пропускается** (CI и релизы продолжают работать).

Корневой `docker-compose.yml` — только локальная разработка (сборка из исходников). Стенд использует `deploy/docker-compose.yml`, проект `studentleague-dev`.

### Куда вставить секреты

GitHub → репозиторий → **Settings → Secrets and variables → Actions**.

#### Secrets (вкладка Secrets)

| Имя | Куда вставить | Что это |
|---|---|---|
| `DEV_SSH_HOST` | Actions secrets | IP или hostname стенда |
| `DEV_SSH_USER` | Actions secrets | Linux-пользователь, под которым CI заходит по SSH |
| `DEV_SSH_KEY` | Actions secrets | **Приватный** ключ целиком, включая строки `-----BEGIN … KEY-----` / `-----END … KEY-----`. Не `.pub`. Пароля на ключе быть не должно. Если GitHub «склеил» переносы — можно вставить как одну строку с `\n`, CI развернёт |
| `DEV_SSH_KEY_BASE64` | Actions secrets, запасной вариант | `base64 -w0 studentleague-deploy` — надёжнее, если `DEV_SSH_KEY` не принимается (`error in libcrypto`) |
| `DEV_SSH_KNOWN_HOSTS` | Actions secrets, **рекомендуется** | Вывод `ssh-keyscan -p 22 YOUR_HOST` (одна или несколько строк). Без него CI делает TOFU через `ssh-keyscan` |
| `DEV_SSH_PORT` | Actions secrets, опционально | Если SSH не на 22 и вы не хотите заводить Variable |
| `DEV_STAND_ENV` | Actions secrets, опционально | Полное содержимое `.env` стенда. CI запишет его в `$DEV_DEPLOY_PATH/.env` **только если файла ещё нет** |

`GITHUB_TOKEN` добавлять не нужно: его выдаёт сам Actions на время job. Скрипт на сервере использует этот токен, чтобы скачать ассеты (в том числе из private-репозитория), и сразу удаляет файл токена.

#### Variables (вкладка Variables)

| Имя | Куда вставить | Значение по умолчанию |
|---|---|---|
| `DEV_DEPLOY_PATH` | Actions variables | `/opt/studentleague` |
| `DEV_SSH_PORT` | Actions variables | `22` |

#### На сервере (не в GitHub)

Файл `$DEV_DEPLOY_PATH/.env` (шаблон `deploy/.env.example`). CI его **не перезаписывает**.

| Переменная | Зачем |
|---|---|
| `JWT_SECRET` | Секрет JWT, длинный (≥32 символа). В профиле `prod` без него backend не стартует |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Единственный админ, создаётся при первом старте |
| `DATABASE_PASSWORD` | Пароль Postgres (контейнер на стенде, порт наружу не публикуется) |
| `CORS_ORIGINS` | Публичный URL стенда, например `http://1.2.3.4` или `https://dev.example.com` |
| `WEB_PORT` | Хостовый порт nginx (по умолчанию `80`) |
| `SPRING_PROFILES_ACTIVE` | На стенде лучше `prod` |

Не кладите SSH-ключ и GitHub token в этот `.env`.

### Один раз на сервере

1. Пользователь для деплоя, Docker Engine + плагин `docker compose`, `curl`, `python3`, `rsync`, `tar`. С сервера должен открываться исходящий HTTPS на `api.github.com` (скачивание релизов).
2. Пользователь в группе `docker` (без интерактивного sudo).
3. Каталог деплоя:

```bash
sudo mkdir -p /opt/studentleague
sudo chown "$USER:$USER" /opt/studentleague
```

4. SSH-ключ только для CI:

```bash
ssh-keygen -t ed25519 -C "github-actions-dev-stand" -f studentleague-deploy -N ""
# studentleague-deploy     → секрет DEV_SSH_KEY
# studentleague-deploy.pub → на сервер
```

На сервере:

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo 'содержимое studentleague-deploy.pub' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Known hosts для секрета `DEV_SSH_KNOWN_HOSTS`:

```bash
ssh-keyscan -p 22 YOUR_HOST
```

5. `.env` на сервере:

```bash
# после первого деплоя папка deploy/ уже будет на сервере, либо скопируйте из репозитория
cp /opt/studentleague/deploy/.env.example /opt/studentleague/.env
chmod 600 /opt/studentleague/.env
# отредактируйте JWT_SECRET, ADMIN_*, DATABASE_PASSWORD, CORS_ORIGINS
```

Либо вставьте готовый текст в секрет `DEV_STAND_ENV` **до** первого деплоя.

6. Можно прогнать проверки:

```bash
DEPLOY_ROOT=/opt/studentleague /opt/studentleague/deploy/bootstrap.sh
```

После этого любой merge в `main` выкатывает стенд сам. Версионный релиз:

```bash
git tag v0.2.0
git push origin v0.2.0
```

Тег `dev` rolling: CI его перезаписывает (`git push --force` только этого тега). Не защищайте тег `dev` в правилах репозитория.

## Как получить релиз прямо сейчас

Релизный workflow **не** запускается на обычный push в ветку. Нужен тег:

```bash
# 1) дождаться зелёного CI на ветке/PR
# 2) создать и запушить тег (из ветки с готовым кодом или после merge в main)
git checkout main
git pull
git tag v0.1.0
git push origin v0.1.0
```

Либо вручную: GitHub → **Actions** → **Release** → **Run workflow** → указать версию (например `0.1.0`).

Для первого релиза **никакие secrets не обязательны**:
- Android APK/AAB соберутся на default keystore
- iOS — unsigned zip (без Apple-сертификатов), job не блокирует релиз если упадёт

Secrets (`ANDROID_KEYSTORE_*`, Apple certs) нужны только для публикации в Google Play / App Store.

Автодеплой на стенд начнётся только после заполнения `DEV_SSH_HOST`, `DEV_SSH_USER`, `DEV_SSH_KEY`.

## Пример релизного потока

```bash
# убедиться, что CI на ветке/main зелёный
git tag v0.1.0
git push origin v0.1.0
# GitHub Actions → Release загружает ассеты в GitHub Release
# GitHub Actions → Dev stand скачивает их по SSH и поднимает Docker
```
