# E2E KronScore (только dev)

Набор Playwright по чек-листу регрессии. Бьёт **только** в dev-стенд.

- Стенд по умолчанию: `http://144.31.153.52:3000`
- Прод `https://itmoliga.ru` набор **отказывается запускать**, если `E2E_BASE_URL` содержит `itmoliga.ru`

Пишущие тесты создают сущности с префиксом `E2E ` и по возможности удаляют их. Чужие турниры, команды и матчи только читают.

## Запуск

```bash
cd e2e
npm ci
npx playwright install chromium
npx playwright test
```

Необязательно:

```bash
export E2E_BASE_URL=http://144.31.153.52:3000
export E2E_FAN_EMAIL=...
export E2E_FAN_PASSWORD=...
# то же для PLAYER, CAPTAIN, REFEREE, ADMIN
```

Роли кроме болельщика без env пропускаются (`test.skip`). Болельщика набор может зарегистрировать сам (`e2e-*@example.com`).

## Секреты GitHub Actions

Workflow `.github/workflows/e2e-dev.yml` — только `workflow_dispatch` и будничное расписание, без деплоя.

| Секрет | Роль |
|---|---|
| `E2E_FAN_EMAIL` / `E2E_FAN_PASSWORD` | Болельщик |
| `E2E_PLAYER_EMAIL` / `E2E_PLAYER_PASSWORD` | Игрок (роль одобрена админом) |
| `E2E_CAPTAIN_EMAIL` / `E2E_CAPTAIN_PASSWORD` | Капитан со своей командой |
| `E2E_REFEREE_EMAIL` / `E2E_REFEREE_PASSWORD` | Судья |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | Админ |

Пароли в репозиторий не класть. Аккаунты `toliktester+*` не использовать.
