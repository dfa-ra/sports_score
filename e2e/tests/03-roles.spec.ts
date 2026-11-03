import { expect, test } from '@playwright/test'
import { apiGet, apiSend } from '../src/api'
import { fanSession, injectSession, registerFan, requireRole, skipWithout } from '../src/accounts'
import { hasRole } from '../src/env'
import { check, title } from '../src/check'
import { firstPublicMatch, firstPublicTeam } from '../src/entities'
import { chromeLink, openChromeMenu } from '../src/ui'

test(title({ id: 'P1' }, 'болельщик не ходит в /admin и /referee; admin API → 403'), check({ id: 'P1' }), async ({ page, request }) => {
  const fan = await fanSession(request)
  const create = await apiSend(request, 'POST', '/tournaments', {
    token: fan.accessToken,
    data: { name: 'E2E fan forbidden', sportId: '11111111-1111-1111-1111-111111111101', seasonYear: 2026, format: 'ROUND_ROBIN' },
  })
  expect(create.status, `POST /tournaments болельщиком → ${create.status}`).toBe(403)

  await injectSession(page, fan)
  await page.goto('/admin')
  await expect(page).not.toHaveURL(/\/admin/)
  await expect(page.getByText(/нет доступа/i)).toBeVisible()
  await page.goto('/referee')
  await expect(page).not.toHaveURL(/\/referee/)
  await expect(page.getByText(/нет доступа/i)).toBeVisible()
})

test(title({ id: 'P2' }, 'капитан не может править чужую команду'), check({ id: 'P2' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  const myId = mine.status === 200 ? String((mine.body as { id?: string }).id || '') : ''
  const foreign = await firstPublicTeam(request, [myId])
  expect(foreign?.id, 'на стенде должна быть хотя бы одна чужая команда').toBeTruthy()
  if (!foreign?.id) throw new Error('нет чужой команды')
  expect(String(foreign.id), 'для проверки нужна чужая команда, не /teams/mine').not.toBe(myId)

  await injectSession(page, captain)
  await page.goto(`/teams/${foreign.id}`)
  await expect(page.getByRole('button', { name: /сохранить карточку|убрать|добавить игрока/i })).toHaveCount(0)

  const update = await apiSend(request, 'PUT', `/teams/${foreign.id}`, {
    token: captain.accessToken,
    data: { name: 'E2E взлом' },
  })
  expect(update.status, `PUT чужой команды → ${update.status}`).toBe(403)
  const del = await apiSend(request, 'DELETE', `/teams/${foreign.id}`, { token: captain.accessToken })
  expect(del.status, `DELETE чужой команды → ${del.status}`).toBe(403)
})

test(title({ id: 'P3', bugs: [20, 21, 22, 23, 24, 25] }, 'судья без назначения не управляет чужим матчем'), check({ id: 'P3', bugs: [20, 21, 22, 23, 24, 25] }), async ({ page, request }) => {
  const reason = skipWithout('REFEREE')
  test.skip(!!reason, reason || '')
  const referee = await requireRole(request, 'REFEREE')
  const match = await firstPublicMatch(request)
  expect(match?.id).toBeTruthy()
  await injectSession(page, referee)
  await page.goto(`/referee/matches/${match.id}`)
  const body = await page.locator('main').innerText()
  const stillOnPad = /\/referee\/matches\//.test(page.url())
  const blocked = /нет доступа|не назначен|недоступ/i.test(body) || !stillOnPad
  expect(blocked, `пульт чужого матча должен быть закрыт: ${page.url()} ${body.slice(0, 200)}`).toBeTruthy()

  for (const path of [
    `/referee/matches/${match.id}/start`,
    `/referee/matches/${match.id}/pause`,
    `/referee/matches/${match.id}/events`,
  ]) {
    const call = await apiSend(request, 'POST', path, {
      token: referee.accessToken,
      data: path.endsWith('/events') ? { eventType: 'GOAL' } : undefined,
    })
    expect(call.status, `POST ${path} → ${call.status}`).toBe(403)
  }
})

test(title({ id: 'P4' }, 'судья не видит админку'), check({ id: 'P4' }), async ({ page, request }) => {
  const reason = skipWithout('REFEREE')
  test.skip(!!reason, reason || '')
  const referee = await requireRole(request, 'REFEREE')
  await injectSession(page, referee)
  await page.goto('/')
  await openChromeMenu(page)
  await expect(await chromeLink(page, 'Админ')).toHaveCount(0)
  await page.goto('/admin')
  await expect(page).not.toHaveURL(/\/admin/)
})

test(title({ id: 'P5', bugs: [15, 16, 17, 18, 19] }, 'роль в UI совпадает с выданными, «Аналитик» сам не появляется'), check({ id: 'P5', bugs: [15, 16, 17, 18, 19] }), async ({ page, request }) => {
  const fan = await fanSession(request)
  await injectSession(page, fan)
  await page.goto('/')
  await openChromeMenu(page)
  await expect(await chromeLink(page, 'Аналитик')).toHaveCount(0)
  await page.goto('/profile')
  const chips = (await page.locator('.badge').allInnerTexts()).join(' ')
  expect(chips, chips).not.toMatch(/аналитик/i)
})

test(title({ id: 'P6' }, 'IDOR: чужой профиль по id нельзя менять'), check({ id: 'P6' }), async ({ request }) => {
  const a = await fanSession(request)
  const b = hasRole('PLAYER') ? await requireRole(request, 'PLAYER') : await registerFan(request, 'idor')
  const otherId = String(b.user.id || '')
  expect(otherId).toBeTruthy()

  const patch = await apiSend(request, 'PATCH', `/admin/users/${otherId}`, {
    token: a.accessToken,
    data: { role: 'ADMIN' },
  })
  expect(patch.status, `PATCH /admin/users/{id} чужого → ${patch.status}`).toBe(403)

  const putPlayer = await apiSend(request, 'PUT', `/players/${otherId}`, {
    token: a.accessToken,
    data: { firstName: 'Хак', lastName: 'Ер' },
  })
  expect(putPlayer.status, `PUT /players/{чужой id} не должен быть 200`).not.toBe(200)

  const after = await apiGet(request, '/auth/me', b.accessToken)
  expect(String((after.body as { firstName?: string }).firstName || '')).not.toBe('Хак')
})
