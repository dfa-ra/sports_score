# E2E KronScore (только dev)

Набор Playwright по чек-листу регрессии. Бьёт **только** в dev-стенд.

- Стенд по умолчанию: `http://144.31.153.52:3000`
- Прод `https://itmoliga.ru` набор **отказывается запускать**, если `E2E_BASE_URL` содержит `itmoliga.ru`

Постоянных фикстур нет. Каждый spec создаёт свои турниры/команды/матчи с префиксом прогона `E2E-<runId>` (`E2E_RUN_ID` или случайный) и снимает их в `afterAll` даже если тест упал. `global-teardown` плюс `npm run cleanup` дочищают всё с префиксом `E2E` (и старый пул «E2E Пул» / «E2E PO …»). Команду `/teams/mine` капитана из `E2E_CAPTAIN_*` не трогаем.

Болельщика без секрета регистрируем на прогон (`e2e-*@example.com`). Регистрационные сценарии (R1/R2/R6) создают свои аккаунты — это сама проверка. API удаления пользователей нет.

## Запуск

```bash
cd e2e
npm ci
npx playwright install chromium
npx playwright test
```

Ручная зачистка leftover на dev:

```bash
export E2E_ADMIN_EMAIL=...
export E2E_ADMIN_PASSWORD=...
npm run cleanup
```

Необязательно:

```bash
export E2E_BASE_URL=http://144.31.153.52:3000
export E2E_RUN_ID=manual1
export E2E_FAN_EMAIL=...
# то же для PLAYER, CAPTAIN, REFEREE, ADMIN
```

Роли кроме болельщика без env пропускаются (`test.skip`).

S3 сравнивает футер с SHA: `E2E_EXPECTED_SHA` или `git ls-remote origin refs/heads/main`.

Раздел 9 (плей-офф, PO1–PO11 + PO6b) и PO12–PO19 — только dev, `E2E_ADMIN_*`. Серия пенальти `skip`, пока на стенде нет `/penalties|shootout`.

Селекторы шапки, «Игр» и сетки не завязаны на старую вёрстку: ролевые ссылки ищутся в шапке и в бургер-меню; календарь работает и с дейтпикером, и с лентой дат. Новые проверки: S6 (бургер), T4 (даты с матчами, окно −3 недели, без дейтпикера), T5 (вкладка «Плей-офф» в таблице), T6 (со страницы матча на турнир), пустые карточки неизвестных матчей в сетке (PO10).

## Что API не удаляет

`DELETE /tournaments/{id}` в контроллере нет (на стенде — 404 «Адрес не найден» или 500). `DELETE /matches/{id}` падает на матчах плей-офф: `playoff_slots.match_id` ссылается на `matches` без `ON DELETE`. После этого `DELETE /teams/{id}?purge=true` не снимает команды с историей матчей. Пользователей и профили удалить нельзя.

Набор в этом случае ставит турнир в `CANCELLED` и пишет leftover в лог. Чтобы они пропали с главной и из «Игр», нужна SQL-чистка на dev (ниже).

### SQL (только leftover с префиксом E2E)

Критерий имён: `name ~ '^E2E([ -]|$)'`. Критерий почты регистраций набора: `email ~ '^e2e[-+].+@example\.com$'`.

Порядок (FK):

```sql
-- 1. Слоты плей-офф и места выхода
DELETE FROM playoff_slots
 WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)');
DELETE FROM tournament_advancing_places
 WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)');

-- 2. Матчи этих турниров и матчи E2E-команд
DELETE FROM match_events WHERE match_id IN (
  SELECT id FROM matches WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)')
     OR home_team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)')
     OR away_team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)')
);
DELETE FROM match_lineup_players WHERE match_id IN (/* тот же SELECT id FROM matches ... */);
DELETE FROM match_referees WHERE match_id IN (/* … */);
DELETE FROM match_availability WHERE match_id IN (/* … */);
DELETE FROM match_analyst_stats WHERE match_id IN (/* … */);
DELETE FROM match_analyst_assignments WHERE match_id IN (/* … */);
DELETE FROM matches
 WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)')
    OR home_team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)')
    OR away_team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)');

-- 3. Заявки, группы, турниры
DELETE FROM tournament_teams
 WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)')
    OR team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)');
DELETE FROM tournament_tables
 WHERE tournament_id IN (SELECT id FROM tournaments WHERE name ~ '^E2E([ -]|$)');
DELETE FROM tournaments WHERE name ~ '^E2E([ -]|$)';

-- 4. Команды (не капитанская /teams/mine, если она тоже с E2E)
DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE name ~ '^E2E([ -]|$)');
DELETE FROM teams WHERE name ~ '^E2E([ -]|$)'
  AND id NOT IN (/* id команды капитана E2E_CAPTAIN, если её нельзя сносить */);

-- 5. Одноразовые аккаунты набора (не E2E_* роли)
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email ~ '^e2e[-+].+@example\.com$');
DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email ~ '^e2e[-+].+@example\.com$');
DELETE FROM device_tokens WHERE user_id IN (SELECT id FROM users WHERE email ~ '^e2e[-+].+@example\.com$');
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email ~ '^e2e[-+].+@example\.com$');
DELETE FROM player_profiles WHERE user_id IN (SELECT id FROM users WHERE email ~ '^e2e[-+].+@example\.com$');
DELETE FROM users WHERE email ~ '^e2e[-+].+@example\.com$';
```

Таблицы: `playoff_slots`, `tournament_advancing_places`, `matches` (+ `match_events`, `match_lineup_players`, `match_referees`, `match_availability`, `match_analyst_stats`, `match_analyst_assignments`), `tournament_teams`, `tournament_tables`, `tournaments`, `team_members`, `teams`, `users`, `player_profiles`, `user_roles`, `refresh_tokens`, `password_reset_tokens`, `device_tokens`.

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
