import { expect, test } from '@playwright/test'
import { apiGet, apiSend, errorCode, errorMessage } from '../src/api'
import { fanSession } from '../src/accounts'
import { check, title } from '../src/check'

const MISSING_UUID = '00000000-0000-0000-0000-000000000000'

function expectBadId(call: { status: number; body: unknown }, path: string) {
  expect(call.status, `${path} → ${call.status} ${JSON.stringify(call.body)}`).toBe(400)
  expect(errorCode(call.body), `${path}: code`).toBe('BAD_REQUEST')
  expect(errorMessage(call.body), `${path}: «${errorMessage(call.body)}»`).toBe('Некорректный идентификатор')
}

function expectMissing(call: { status: number; body: unknown }, path: string) {
  expect(call.status, `${path} → ${call.status} ${JSON.stringify(call.body)}, не 500`).toBe(404)
  expect(errorCode(call.body), `${path}: code`).toBe('NOT_FOUND')
}

test(title({ id: 'E1', bugs: [1] }, 'матч: мусорный id → 400, несуществующий UUID → 404, страница «не найдено»'), check({ id: 'E1', bugs: [1] }), async ({ page, request }) => {
  expectBadId(await apiGet(request, '/matches/999999'), 'GET /matches/999999')
  expectMissing(await apiGet(request, `/matches/${MISSING_UUID}`), 'GET /matches/<uuid>')

  await page.goto('/matches/999999')
  await expect(page.getByText(/матч не найден|не найдено|такой страницы нет/i)).toBeVisible()
  await page.goto(`/matches/${MISSING_UUID}`)
  await expect(page.getByText(/матч не найден|не найдено|такой страницы нет/i)).toBeVisible()
})

test(title({ id: 'E2', bugs: [26, 27, 28, 29] }, 'команда/турнир: мусорный id → 400, несуществующий UUID → 404'), check({ id: 'E2', bugs: [26, 27, 28, 29] }), async ({ page, request }) => {
  expectBadId(await apiGet(request, '/teams/new'), 'GET /teams/new')
  expectBadId(await apiGet(request, '/tournaments/not-a-tournament'), 'GET /tournaments/мусор')
  expectMissing(await apiGet(request, `/teams/${MISSING_UUID}`), 'GET /teams/<uuid>')
  expectMissing(await apiGet(request, `/tournaments/${MISSING_UUID}`), 'GET /tournaments/<uuid>')

  await page.goto('/teams/new')
  await expect(page.getByText(/нет такой|не найден|такой страницы нет|некорректн/i)).toBeVisible()
  await page.goto(`/teams/${MISSING_UUID}`)
  await expect(page.getByText(/нет такой|не найден|такой страницы нет/i)).toBeVisible()
  await page.goto('/tournaments/not-a-tournament')
  await expect(page.getByText(/нет такой|не найден|такой страницы нет|некорректн/i)).toBeVisible()
  await page.goto(`/tournaments/${MISSING_UUID}`)
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
    { path: '/matches', data: { tournamentId: MISSING_UUID } },
    { path: '/players/me', method: 'PUT' as const, data: { firstName: 'E2E', lastName: 'NoAuth' } },
    { path: '/auth/change-password-email', data: undefined },
  ]
  for (const item of posts) {
    const call = await apiSend(request, item.method || 'POST', item.path, { data: item.data })
    expect(call.status, `${item.method || 'POST'} ${item.path} без токена → ${call.status}`).toBe(401)
  }
})
