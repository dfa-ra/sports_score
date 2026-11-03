import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, errorMessage } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { createMatch, createTournament, idOf } from '../src/entities'
import { createLivePool, deleteMatch, disposeSpec, excludeTeamFromTournament } from '../src/pool'
import { entityName } from '../src/run'
import { Tracker } from '../src/tracker'
import { waitForApp } from '../src/ui'

const bag = new Tracker()

test.afterAll(async ({ request }) => {
  await disposeSpec(request, bag, '04-admin')
})

test(title({ id: 'A1' }, 'создать турнир «E2E …» с корректными датами → виден публично'), check({ id: 'A1' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const created = await createTournament(request, admin, {
    name: entityName('публичный'),
    startDate: '2026-11-10',
    endDate: '2026-12-10',
    track: bag,
  })
  expect(created.status, JSON.stringify(created.body)).toBe(201)
  const id = idOf(created.body)
  const list = await apiGet(request, '/tournaments?size=100')
  expect(asList(list.body).some((row) => row.id === id), 'новый турнир должен быть в публичном списке').toBeTruthy()
})

test(title({ id: 'A2', bugs: [15, 16, 17, 18, 19] }, 'дата окончания раньше начала — ошибка на фронте и бэке'), check({ id: 'A2', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const created = await createTournament(request, admin, {
    name: entityName('кривые даты'),
    startDate: '2026-12-20',
    endDate: '2026-11-01',
    track: bag,
  })
  expect(created.status, `бэкенд должен отклонить end < start: ${created.status} ${JSON.stringify(created.body)}`).toBeGreaterThanOrEqual(400)
  expect(errorMessage(created.body).length, 'нужно сообщение валидации').toBeGreaterThan(0)

  await injectSession(page, admin)
  await page.goto('/admin')
  await page.getByRole('button', { name: 'Турниры' }).click()
  const form = page.locator('form').filter({ hasText: 'Создать турнир' })
  await form.getByLabel('Название').fill(entityName('кривые даты форма'))
  await form.getByLabel('Старт').fill('2026-12-20')
  await form.getByLabel('Финиш').fill('2026-11-01')
  await form.getByRole('button', { name: /создать турнир/i }).click()
  await expect(form.locator('.form-error')).toBeVisible()
})

test(title({ id: 'A3', bugs: [15, 16, 17, 18, 19] }, 'в настройках турнира можно редактировать даты'), check({ id: 'A3', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const created = await createTournament(request, admin, { name: entityName('даты'), track: bag })
  expect(created.status).toBe(201)
  await injectSession(page, admin)
  await page.goto(`/tournaments/${idOf(created.body)}`)
  await waitForApp(page)
  await expect(page.getByText('Настройки турнира')).toBeVisible()
  await expect(page.getByLabel(/старт|начало|дата начала/i)).toBeVisible()
  await expect(page.getByLabel(/финиш|окончани|дата окончания/i)).toBeVisible()
})

test(title({ id: 'A4' }, 'создать команду, заявить и допустить в турнир'), check({ id: 'A4' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const pool = await createLivePool(request, admin, bag, 'A4')
  const entries = asList((await apiGet(request, `/tournaments/${pool.tournamentId}/teams`)).body)
  const home = entries.find((row) => String(row.teamId || row.id) === pool.homeId)
  expect(String(home?.status || ''), 'хозяева должны быть допущены').toBe('APPROVED')
})

test(title({ id: 'A5', bugs: [15, 16, 17, 18, 19] }, '«Заявиться»: модалка закрывается и показывает подтверждение'), check({ id: 'A5', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const pool = await createLivePool(request, admin, bag, 'A5')
  await excludeTeamFromTournament(request, admin, pool.tournamentId, pool.applyTeamId)
  await injectSession(page, admin)
  await page.goto(`/tournaments/${pool.tournamentId}`)
  await waitForApp(page)
  await page.getByRole('button', { name: 'Заявиться' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText(/смотрим составы/i)).toBeHidden()
  const picker = dialog.locator('#apply-team-select')
  await expect(picker.or(dialog.getByText(/заявить «/i))).toBeVisible()
  if (await picker.count()) {
    await picker.selectOption({ label: pool.applyName })
  }
  await dialog.getByRole('button', { name: 'Заявиться' }).click()
  await expect(page.getByText(/заявка ушла|подтвержд|ждём допуск/i)).toBeVisible()
  await expect(dialog).toBeHidden({ timeout: 8_000 })
})

test(title({ id: 'A6' }, 'создать матч: одинаковые команды — ошибка; разные — успех'), check({ id: 'A6' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const pool = await createLivePool(request, admin, bag, 'A6')
  const same = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.homeId,
    track: bag,
  })
  expect(same.status, `одинаковые команды → ${same.status} ${JSON.stringify(same.body)}`).toBeGreaterThanOrEqual(400)

  const ok = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
    track: bag,
  })
  expect(ok.status, JSON.stringify(ok.body)).toBe(201)
  await deleteMatch(request, admin, idOf(ok.body))
})

test(title({ id: 'A7', bugs: [15, 16, 17, 18, 19] }, 'назначить судью; список аналитиков содержит всех с ролью'), check({ id: 'A7', bugs: [15, 16, 17, 18, 19] }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const users = await apiGet(request, '/admin/users?size=100', admin.accessToken)
  expect(users.status).toBe(200)
  const analysts = asList(users.body).filter((row) => {
    const roles = Array.isArray(row.roles) ? row.roles as { role?: string; status?: string }[] : []
    return row.role === 'ANALYST' || roles.some((item) => item.role === 'ANALYST' && item.status === 'APPROVED')
  })
  const listed = await apiGet(request, '/referee/analysts', admin.accessToken)
  expect(listed.status, `GET /referee/analysts → ${listed.status}`).toBe(200)
  const listedIds = new Set(asList(listed.body).map((row) => String(row.id)))
  for (const person of analysts) {
    expect(listedIds.has(String(person.id)), `аналитик ${person.email} должен быть в списке назначения`).toBeTruthy()
  }

  if (skipWithout('REFEREE')) return
  const referee = await requireRole(request, 'REFEREE')
  const pool = await createLivePool(request, admin, bag, 'A7')
  const match = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
    track: bag,
  })
  expect(match.status, JSON.stringify(match.body)).toBe(201)
  const result = await apiSend(request, 'POST', `/matches/${idOf(match.body)}/referees`, {
    token: admin.accessToken,
    data: { refereeId: referee.user.id },
  })
  expect(result.status, JSON.stringify(result.body)).toBe(201)
})
