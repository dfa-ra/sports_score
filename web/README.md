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

## Локальный запуск

Требуется запущенный backend на `http://localhost:8080` (или proxy Vite).

```bash
npm install
npm run dev
```

Production-сборка:

```bash
npm run build
npm run preview
```

## Основные разделы

- Публичные: Home, турниры, матчи (live), команды, игроки, статистика, login/register
- Admin: дашборд со вкладками users / tournaments / matches / teams / players / referees / statistics
- Referee: список назначенных матчей и Live Match Control с крупными кнопками

## CI

Сборка web входит в GitHub Actions CI и в релизный tarball — см. [docs/ci.md](../docs/ci.md).
