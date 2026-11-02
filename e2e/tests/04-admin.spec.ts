import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import {
  applyTeam,
  approveTeam,
  cleanupCreated,
  createMatch,
  createTeam,
  createTournament,
  emptyIds,
  idOf,
} from '../src/entities'
import { uniqueEntityName } from '../src/env'

test(title({ id: 'A1' }, 'создать турнир «E2E …» с корректными датами → виден публично'), check({ id: 'A1' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const ids = emptyIds()
  const created = await createTournament(request, admin, { name: uniqueEntityName('кубок') })
  expect(created.status, JSON.stringify(created.body)).toBe(201)
  ids.tournamentId = idOf(created.body)
  try {
    const list = await apiGet(request, '/tournaments?size=50')
    const found = asList(list.body).some((row) => row.id === ids.tournamentId)
    expect(found, 'турнир должен появиться в публичном списке').toBeTruthy()
  } finally {
    await cleanupCreated(request, admin, ids)
  }
})

test(title({ id: 'A2', bugs: [15, 16, 17, 18, 19] }, 'дата окончания раньше начала — ошибка на фронте и бэке'), check({ id: 'A2', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const created = await createTournament(request, admin, {
    name: uniqueEntityName('кривые даты'),
    startDate: '2026-12-20',
    endDate: '2026-11-01',
  })
  expect(created.status, `бэкенд должен отклонить end < start: ${created.status} ${JSON.stringify(created.body)}`).toBeGreaterThanOrEqual(400)
  expect(errorMessage(created.body).length, 'нужно сообщение валидации').toBeGreaterThan(0)

  await injectSession(page, admin)
  await page.goto('/admin')
  await page.getByRole('button', { name: 'Турниры' }).click()
  const form = page.locator('form').filter({ hasText: 'Создать турнир' })
  await form.getByLabel('Название').fill(uniqueEntityName('форма дат'))
  await form.getByLabel('Старт').fill('2026-12-20')
  await form.getByLabel('Финиш').fill('2026-11-01')
  await form.getByRole('button', { name: /создать турнир/i }).click()
  await expect(form.locator('.form-error')).toBeVisible()
})

test(title({ id: 'A3', bugs: [15, 16, 17, 18, 19] }, 'в настройках турнира можно редактировать даты'), check({ id: 'A3', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const ids = emptyIds()
  const created = await createTournament(request, admin)
  ids.tournamentId = idOf(created.body)
  try {
    await injectSession(page, admin)
    await page.goto(`/tournaments/${ids.tournamentId}`)
    await expect(page.getByText('Настройки турнира')).toBeVisible()
    await expect(page.getByLabel(/старт|начало|дата начала/i)).toBeVisible()
    await expect(page.getByLabel(/финиш|окончани|дата окончания/i)).toBeVisible()
  } finally {
    await cleanupCreated(request, admin, ids)
  }
})

test(title({ id: 'A4' }, 'создать команду, заявить и допустить в турнир'), check({ id: 'A4' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const ids = emptyIds()
  const tournament = await createTournament(request, admin)
  ids.tournamentId = idOf(tournament.body)
  const team = await createTeam(request, admin)
  expect(team.status, JSON.stringify(team.body)).toBe(201)
  ids.teamIds.push(idOf(team.body))
  try {
    const applied = await applyTeam(request, admin, ids.tournamentId, ids.teamIds[0])
    expect(applied.status, JSON.stringify(applied.body)).toBe(201)
    const approved = await approveTeam(request, admin, ids.tournamentId, ids.teamIds[0])
    expect(approved.status, JSON.stringify(approved.body)).toBe(200)
    expect(String(asRecord(approved.body).status || '')).toBe('APPROVED')
  } finally {
    await cleanupCreated(request, admin, ids)
  }
})

test(title({ id: 'A5', bugs: [15, 16, 17, 18, 19] }, '«Заявиться»: модалка закрывается и показывает подтверждение'), check({ id: 'A5', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const ids = emptyIds()
  const tournament = await createTournament(request, admin)
  ids.tournamentId = idOf(tournament.body)
  const team = await createTeam(request, admin)
  ids.teamIds.push(idOf(team.body))
  try {
    await injectSession(page, admin)
    await page.goto(`/tournaments/${ids.tournamentId}`)
    await page.getByRole('button', { name: 'Заявиться' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: 'Заявиться' }).click()
    await expect(page.getByText(/заявка ушла|подтвержд|ждём допуск/i)).toBeVisible()
    await expect(dialog).toBeHidden({ timeout: 8_000 })
  } finally {
    await cleanupCreated(request, admin, ids)
  }
})

test(title({ id: 'A6' }, 'создать матч: одинаковые команды — ошибка; разные — успех'), check({ id: 'A6' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const ids = emptyIds()
  const tournament = await createTournament(request, admin)
  ids.tournamentId = idOf(tournament.body)
  const home = await createTeam(request, admin)
  const away = await createTeam(request, admin)
  ids.teamIds.push(idOf(home.body), idOf(away.body))
  try {
    for (const teamId of ids.teamIds) {
      await applyTeam(request, admin, ids.tournamentId, teamId)
      await approveTeam(request, admin, ids.tournamentId, teamId)
    }
    const same = await createMatch(request, admin, {
      tournamentId: ids.tournamentId,
      homeTeamId: ids.teamIds[0],
      awayTeamId: ids.teamIds[0],
    })
    expect(same.status, `одинаковые команды → ${same.status} ${JSON.stringify(same.body)}`).toBeGreaterThanOrEqual(400)

    const ok = await createMatch(request, admin, {
      tournamentId: ids.tournamentId,
      homeTeamId: ids.teamIds[0],
      awayTeamId: ids.teamIds[1],
    })
    expect(ok.status, JSON.stringify(ok.body)).toBe(201)
    ids.matchIds.push(idOf(ok.body))
  } finally {
    await cleanupCreated(request, admin, ids)
  }
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
  const ids = emptyIds()
  const tournament = await createTournament(request, admin)
  ids.tournamentId = idOf(tournament.body)
  const home = await createTeam(request, admin)
  const away = await createTeam(request, admin)
  ids.teamIds.push(idOf(home.body), idOf(away.body))
  try {
    for (const teamId of ids.teamIds) {
      await applyTeam(request, admin, ids.tournamentId, teamId)
      await approveTeam(request, admin, ids.tournamentId, teamId)
    }
    const match = await createMatch(request, admin, {
      tournamentId: ids.tournamentId,
      homeTeamId: ids.teamIds[0],
      awayTeamId: ids.teamIds[1],
    })
    ids.matchIds.push(idOf(match.body))
    const assigned = await apiSend(request, 'POST', `/matches/${ids.matchIds[0]}/referees`, {
      token: admin.accessToken,
      data: { refereeId: referee.user.id },
    })
    expect(assigned.status, JSON.stringify(assigned.body)).toBe(201)
  } finally {
    await cleanupCreated(request, admin, ids)
  }
})
