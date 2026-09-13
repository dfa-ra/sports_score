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
| Android APK + AAB | Flutter `--release` на прод: `--dart-define=API_BASE_URL=https://itmoliga.ru/api/v1` (пока default keystore) |
| Закрытый dev APK | Prerelease-тег `dev` → `student-league-dev-android.apk` на `http://144.31.153.52:3000/api/v1` |
| iOS unsigned zip | `Runner.app` с `macos-latest` (`--no-codesign`); опционально / не блокирует релиз |

Текст релиза — короткий список коммитов (`scripts/release_notes.sh`), без названий PR и без автоchangelog GitHub.

После публикации релиза `v*` workflow **Prod stand** качает эти ассеты на прод. Dev по-прежнему обновляется с каждого push в `main`.

### Подпись Android (готово к Store)

Добавьте secrets репозитория и расширьте job `android-release`:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

В CI нужно декодировать keystore и передать `key.properties` / Gradle properties перед `flutter build appbundle --release`.

### Подпись iOS (App Store)

Нужны сертификаты Apple, provisioning profiles и желательно [Fastlane Match](https://docs.fastlane.tools/actions/match/). Текущий job только проверяет, что iOS-таргет собирается без codesign.

## Два стенда: dev и prod

Не ветки git, а **два сервера** и два workflow.

| Контур | Workflow | Когда едет | Куда |
|---|---|---|---|
| **Dev** | `stand.yml` (Dev stand) | Push в `main` или Actions → Dev stand | Секреты `DEV_*` |
| **Prod** | `release.yml` (job SSH deploy to prod) и запасной `prod.yml` | Тег `v*` → Release собирает артефакты и сразу деплоит prod. `prod.yml` сам по `release` не стартует, если релиз создан `GITHUB_TOKEN` | Секреты `PROD_*` |

Оба вызывают один и тот же SSH-деплой (`deploy-remote.yml` → `deploy/stand.sh`). Caddy проксирует на nginx. **Dev** публикует только HTTP (`WEB_PORT`, обычно `:3000`) — на старом VPS `:443` уже занят. **Prod** дополнительно берёт `deploy/docker-compose.tls.yml` (`:443` tcp/udp) для Let's Encrypt. Нужен **DNS-имя**, не голый IP.

Если SSH-секреты контура пустые, деплой этого контура **пропускается**.

Корневой `docker-compose.yml` — только локалка. Стенд: `deploy/docker-compose.yml`. Имена проектов: `studentleague-dev` и `studentleague-prod`.

### Куда вставить секреты

GitHub → репозиторий → **Settings → Secrets and variables → Actions**.

#### Dev — уже может быть заполнено

| Имя | Тип | Что это |
|---|---|---|
| `DEV_SSH_HOST` | secret | IP или hostname **dev**-сервера |
| `DEV_SSH_USER` | secret | Linux-пользователь для SSH |
| `DEV_SSH_KEY` | secret | Приватный ключ целиком (`BEGIN`/`END`). Не `.pub`, без пароля |
| `DEV_SSH_KEY_BASE64` | secret, запасной | `base64 -w0 studentleague-deploy-dev` |
| `DEV_SSH_KNOWN_HOSTS` | secret, рекомендуется | `ssh-keyscan -p 22 DEV_HOST` |
| `DEV_STAND_ENV` | secret, опционально | Текст `.env` dev. Пишется только если файла ещё нет |
| `DEV_DEPLOY_PATH` | variable | По умолчанию `/opt/studentleague` |
| `DEV_SSH_PORT` | variable | По умолчанию `22` |

#### Prod — вставить для нового сервера

Те же поля, префикс `PROD_`. **Другой SSH-ключ**, не копия dev.

| Имя | Тип | Что это |
|---|---|---|
| `PROD_SSH_HOST` | secret | IP или hostname **prod**-сервера (тот DE-R9-8) |
| `PROD_SSH_USER` | secret | Linux-пользователь для SSH |
| `PROD_SSH_KEY` | secret | Приватный ключ prod (`ssh-keygen -t ed25519 -C "github-actions-prod" -N ""`) |
| `PROD_SSH_KEY_BASE64` | secret, запасной | `base64 -w0 studentleague-deploy-prod` |
| `PROD_SSH_KNOWN_HOSTS` | secret, рекомендуется | `ssh-keyscan -p 22 PROD_HOST` |
| `PROD_STAND_ENV` | secret, опционально | Текст `.env` prod из `deploy/.env.prod.example`. Только первый раз |
| `PROD_DEPLOY_PATH` | variable | По умолчанию `/opt/studentleague` |
| `PROD_SSH_PORT` | variable | По умолчанию `22` |

`GITHUB_TOKEN` добавлять не нужно.

На GitHub появятся environments `development` и `production` — на prod можно включить required reviewers.

#### На каждом сервере (не в GitHub)

Файл `/opt/studentleague/.env`. CI его **не перезаписывает**.

Dev: шаблон `deploy/.env.example`. Prod: `deploy/.env.prod.example` (**другие** JWT и пароль БД).

| Переменная | Зачем |
|---|---|
| `JWT_SECRET` | ≥32 символа, свой на каждый стенд |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Админ при первом старте |
| `DATABASE_PASSWORD` | Postgres внутри Docker |
| `CORS_ORIGINS` | Публичный URL, для prod `https://itmoliga.ru,https://www.itmoliga.ru` |
| `CADDY_FILE` | Prod: `Caddyfile.prod` (itmoliga.ru + www) |
| `CADDY_SITE` | Dev без домена: `http://:80`. Prod задаёт Caddyfile.prod |
| `CADDY_EMAIL` | Почта для Let's Encrypt (prod) |
| `CADDY_HTTP_PORT` | Хостовый порт Caddy :80. Старый стенд: `3000`. Prod: `80` |
| `CADDY_HTTPS_PORT` | Prod: `443`. На dev не публикуется |
| `APP_DEMO_DATA` | Dev `true`, prod `false` |
| `COMPOSE_PROJECT_NAME` | `studentleague-dev` / `studentleague-prod` |

Для TLS на prod: **A-запись `itmoliga.ru` и `www` должна смотреть на IP VPS**, не на shared-хостинг регистратора (парковочная страница «домен привязан к хостингу»). В файрволе открыты **80 и 443**. Caddy сам возьмёт сертификат.

Не кладите SSH-ключ и GitHub token в `.env`.

### Один раз на сервере

1. Пользователь для деплоя, Docker Engine + плагин `docker compose`, `curl`, `python3`, `tar`. С сервера должен открываться исходящий HTTPS на `api.github.com` (скачивание релизов). CI заливает `deploy/` через `tar` по SSH, `rsync` на VPS не нужен.
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
# dev
cp /opt/studentleague/deploy/.env.example /opt/studentleague/.env
# prod
cp /opt/studentleague/deploy/.env.prod.example /opt/studentleague/.env
chmod 600 /opt/studentleague/.env
# JWT_SECRET, ADMIN_*, DATABASE_PASSWORD, CORS_ORIGINS, CADDY_SITE, CADDY_EMAIL
```

Либо вставьте готовый текст в `DEV_STAND_ENV` / `PROD_STAND_ENV` **до** первого деплоя.

6. Можно прогнать проверки:

```bash
DEPLOY_ROOT=/opt/studentleague /opt/studentleague/deploy/bootstrap.sh
```

Merge в `main` → только **dev**. Тег `v*` → GitHub Release → **prod**.

```bash
git tag v0.2.6
git push origin v0.2.6
```

Тег `dev` rolling: CI его перезаписывает. Не защищайте тег `dev`.

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

Автодеплой на **dev** — после `DEV_SSH_*`. На **prod** — после `PROD_SSH_*` и тега `v*`.

## Пример релизного потока

```bash
# main зелёный → dev уже обновился сам
git tag v0.2.6
git push origin v0.2.6
# Actions → Release собирает jar/web/apk и сам качает их на прод (Docker + Caddy)
```
