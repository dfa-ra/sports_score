# E2E KronScore (только dev)

Набор Playwright по чек-листу регрессии. Бьёт **только** в dev-стенд.

- Стенд по умолчанию: `http://144.31.153.52:3000`
- Прод `https://itmoliga.ru` набор **отказывается запускать**, если `E2E_BASE_URL` содержит `itmoliga.ru`

Пишущие тесты переиспользуют постоянный пул на dev и не плодят десятки одноразовых турниров:

- турнир `E2E Пул`
- команды `E2E Хозяева`, `E2E Гости`, `E2E Заявка`
- капитанская `E2E Команда` — постоянная, набор её не создаёт и не удаляет

`DELETE /tournaments/{id}` на стенде даёт 500, поэтому турниры пула не удаляем: отменяем только одноразовые, матчи после live-проверок снимаем. Болельщика без секрета регистрируем один раз на прогон (`e2e-*@example.com`). Регистрационные сценарии (R1/R2/R6) по-прежнему создают свои аккаунты — это сама проверка.

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

S3 сравнивает футер с SHA: `E2E_EXPECTED_SHA` или `git ls-remote origin refs/heads/main`. Если ни того ни другого нет (прогон вне git) — skip.

Раздел 9 (плей-офф, PO1–PO11) бьёт только в dev и только с `E2E_ADMIN_*`. Пишет по чек-листу и PR #56. Если `GET /tournaments/{id}/playoff` на стенде отдаёт «Файл не найден» (фича не задеплоена) — все PO-тесты `skip`. PO11 всегда skip: матча за 3-е место в PR #56 нет, это вопрос к Костику. Фикстуры переиспользуют `E2E PO 01`…`16` и турниры `E2E PO-2` / `PO-4` / `PO-8` / `PO-16` / `PO-snap` / `PO-sizes`.

E1/E2: мусорный id → 400 «Некорректный идентификатор»; корректный, но несуществующий UUID → 404. S2 сверяет маршруты с роутером main (`/gallery` и `/about` не проверяем). R9 шлёт `firstName`/`lastName` как в коде API; расхождение с `docs/api.md` только в логе. E6–E12 — API-дыры: email судей, повторный lineups, фейковый jpg, битый JSON, `/auth/me` ролей, минуты события, 429 на одноразовом `e2e-ratelimit-*` (не на базовых E2E_*).

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
