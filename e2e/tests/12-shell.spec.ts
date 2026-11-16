import { expect, test } from '@playwright/test'
import { apiGet, asList, asRecord } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { createMatch } from '../src/entities'
import { createLivePool, disposeSpec } from '../src/pool'
import { Tracker } from '../src/tracker'
import {
  calendarDayButtons,
  chromeLink,
  datePicker,
  openChromeMenu,
  waitForApp,
} from '../src/ui'

const bag = new Tracker()

test.afterAll(async ({ request }) => {
  await disposeSpec(request, bag, '12-shell')
})

function isoDaysAgo(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return date.toISOString()
}

test(title({ id: 'S6' }, 'ролевые пункты шапки доступны из меню-бургера или навигации'), check({ id: 'S6' }), async ({ page, request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  await injectSession(page, admin)
  await page.goto('/')
  await waitForApp(page)
  await openChromeMenu(page)
  const adminLink = await chromeLink(page, /админ/i)
  await expect(adminLink.first(), 'админ должен открываться из шапки или бургера').toBeVisible()
  await adminLink.first().click()
  await expect(page).toHaveURL(/\/admin/)
})

test(title({ id: 'T4' }, 'Игры: только даты с матчами, окно −3 недели, без дейтпикера'), check({ id: 'T4' }), async ({ page, request }) => {
  test.setTimeout(120_000)
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const pool = await createLivePool(request, admin, bag, 'календарь')
  const recent = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
    scheduledAt: isoDaysAgo(2),
    track: bag,
  })
  const old = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
    scheduledAt: isoDaysAgo(28),
    track: bag,
  })
  expect(recent.status).toBe(201)
  expect(old.status).toBe(201)
  const recentDay = String(asRecord(recent.body).scheduledAt).slice(0, 10)
  const oldDay = String(asRecord(old.body).scheduledAt).slice(0, 10)

  await page.goto('/calendar')
  await waitForApp(page)
  await expect(datePicker(page), 'в «Играх» больше нет дейтпикера').toHaveCount(0)
  const buttons = calendarDayButtons(page)
  await expect(buttons.first(), 'лента дат').toBeVisible()
  const labels = (await buttons.allInnerTexts()).join('\n')
  expect(labels, `день с матчем ${recentDay} должен быть в ленте`).toMatch(new RegExp(recentDay.slice(8)))
  expect(labels, `день ${oldDay} старше 3 недель не должен быть в ленте`).not.toMatch(new RegExp(`${oldDay.slice(8)}\\.${oldDay.slice(5, 7)}`))
})

test(title({ id: 'T5' }, 'в «Таблице» есть вкладка «Плей-офф»'), check({ id: 'T5' }), async ({ page }) => {
  await page.goto('/table')
  await waitForApp(page)
  const tab = page.getByRole('button', { name: /плей-офф/i }).or(page.getByRole('tab', { name: /плей-офф/i }))
  await expect(tab.first(), 'вкладка «Плей-офф» в таблице').toBeVisible()
  await tab.first().click()
  await expect(page.locator('main')).toBeVisible()
})

test(title({ id: 'T6' }, 'со страницы матча можно перейти в его турнир'), check({ id: 'T6' }), async ({ page, request }) => {
  const listed = asList((await apiGet(request, '/matches?size=20&sort=scheduledAt,desc')).body)
  let match = listed.find((row) => row.tournamentId)
  if (!match && !skipWithout('ADMIN')) {
    const admin = await requireRole(request, 'ADMIN')
    const pool = await createLivePool(request, admin, bag, 'матч-турнир')
    const created = await createMatch(request, admin, {
      tournamentId: pool.tournamentId,
      homeTeamId: pool.homeId,
      awayTeamId: pool.awayId,
      track: bag,
    })
    match = { id: asRecord(created.body).id, tournamentId: pool.tournamentId }
  }
  test.skip(!match?.id || !match.tournamentId, 'нет матча с турниром')
  const tournamentId = String(match?.tournamentId)
  const tournament = asRecord((await apiGet(request, `/tournaments/${tournamentId}`)).body)
  await page.goto(`/matches/${match?.id}`)
  await waitForApp(page)
  const link = page.locator(`a[href="/tournaments/${tournamentId}"]`)
    .or(page.getByRole('link', { name: tournament.name ? String(tournament.name) : /турнир/i }))
  await expect(link.first(), 'название турнира на карточке матча — ссылка').toBeVisible()
  await link.first().click()
  await expect(page).toHaveURL(new RegExp(`/tournaments/${tournamentId}`))
})
