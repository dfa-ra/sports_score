import { expect, test } from '@playwright/test'
import { apiGet, apiSend, errorCode } from '../src/api'
import { fanSession } from '../src/accounts'
import { check, title } from '../src/check'

test(title({ id: 'E1', bugs: [1] }, 'несуществующий матч: API 404 и страница «не найдено»'), check({ id: 'E1', bugs: [1] }), async ({ page, request }) => {
  const numeric = await apiGet(request, '/matches/999999')
  expect(numeric.status, `GET /matches/999999 → ${numeric.status} ${JSON.stringify(numeric.body)}`).toBe(404)
  expect(errorCode(numeric.body)).toBe('NOT_FOUND')

  const missing = await apiGet(request, '/matches/00000000-0000-0000-0000-000000000000')
  expect(missing.status, 'несуществующий UUID матча → 404, не 500').toBe(404)
  expect(errorCode(missing.body)).toBe('NOT_FOUND')

  await page.goto('/matches/999999')
  await expect(page.getByText(/матч не найден|не найдено|такой страницы нет/i)).toBeVisible()
})

test(title({ id: 'E2', bugs: [26, 27, 28, 29] }, 'мусорные id команды и турнира → 404 / «нет такой»'), check({ id: 'E2', bugs: [26, 27, 28, 29] }), async ({ page, request }) => {
  const team = await apiGet(request, '/teams/new')
  expect(team.status, `GET /teams/new → ${team.status} ${JSON.stringify(team.body)}`).toBe(404)
  const tournament = await apiGet(request, '/tournaments/not-a-tournament')
  expect(tournament.status, `GET /tournaments/мусор → ${tournament.status}`).toBe(404)

  await page.goto('/teams/new')
  await expect(page.getByText(/нет такой|не найден|такой страницы нет/i)).toBeVisible()
  await page.goto('/tournaments/not-a-tournament')
  await expect(page.getByText(/нет такой|не найден|такой страницы нет/i)).toBeVisible()
})

test(title({ id: 'E3', bugs: [2] }, 'отсутствующий файл /media → 404'), check({ id: 'E3', bugs: [2] }), async ({ request }) => {
  const missing = await apiGet(request, '/media/e2e-does-not-exist-404.jpg')
  expect(missing.status, `GET /media/отсутствует → ${missing.status} ${JSON.stringify(missing.body)}`).toBe(404)
})

test(title({ id: 'E4', bugs: [15, 16, 17, 18, 19] }, 'болельщик: /referees и /users → 403, не 500'), check({ id: 'E4', bugs: [15, 16, 17, 18, 19] }), async ({ request }) => {
  const fan = await fanSession(request)
  for (const path of ['/admin/referees', '/admin/users']) {
    const call = await apiGet(request, path, fan.accessToken)
    expect(call.status, `GET ${path} токеном болельщика → ${call.status} ${JSON.stringify(call.body)}`).toBe(403)
  }
  for (const path of ['/referees', '/users']) {
    const call = await apiGet(request, path, fan.accessToken)
    expect(call.status, `GET ${path} не должен отдавать список: ${call.status}`).not.toBe(200)
    expect(call.status, `${path} не должен отвечать 500`).not.toBe(500)
  }
})

test(title({ id: 'E5' }, 'без токена мутации → 401'), check({ id: 'E5' }), async ({ request }) => {
  const posts = [
    { path: '/tournaments', data: { name: 'E2E noauth' } },
    { path: '/teams', data: { name: 'E2E noauth' } },
    { path: '/matches', data: { tournamentId: '00000000-0000-0000-0000-000000000000' } },
    { path: '/players/me', method: 'PUT' as const, data: { firstName: 'E2E', lastName: 'NoAuth' } },
    { path: '/auth/change-password-email', data: undefined },
  ]
  for (const item of posts) {
    const call = await apiSend(request, item.method || 'POST', item.path, { data: item.data })
    expect(call.status, `${item.method || 'POST'} ${item.path} без токена → ${call.status}`).toBe(401)
  }
})
