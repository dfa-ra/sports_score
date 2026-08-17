# Student League — Web

Клиентское Vue 3 + TypeScript + Vite приложение студенческой спортивной лиги.

## Стек

- Vue 3 (`<script setup>`)
- TypeScript
- Vite
- Pinia (auth store)
- Vue Router
- Axios (+ refresh interceptor)
- STOMP / SockJS для live-счёта

## Запуск

В Docker вместе с backend (рекомендуется на сервере):

```bash
docker compose up -d --build web
```

Сайт: `http://localhost` (или `WEB_PORT` из `.env`). Контейнер сам отдаёт статику и проксирует `/api`, `/ws`, `/media`.

Локальная разработка с hot reload — нужен backend на `http://localhost:8080`:

```bash
npm install
npm run dev
```

Production-сборка без Docker:

```bash
npm run build
npm run preview
```

## Основные разделы

Каталог открыт без регистрации. Создание команд, турниров, матчей и смена ролей — в интерфейсе (профиль, карточки, админ-панель). Swagger закрыт.

- Публичные: Home, турниры, матчи (live), команды, игроки, статистика, login/register
- Admin: дашборд со вкладками users / tournaments / matches / teams / players / referees / statistics
- Referee: список назначенных матчей и Live Match Control с крупными кнопками

## CI

Сборка web входит в GitHub Actions CI и в релизный tarball — см. [docs/ci.md](../docs/ci.md).
