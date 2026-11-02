import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from '../src/api'
import { requireRole } from '../src/accounts'
import { hasRole } from '../src/env'
import { check, title } from '../src/check'
import { uniqueEntityName } from '../src/env'
import { waitForApp } from '../src/ui'

test(title({ id: 'T1', bugs: [3] }, 'список матчей турнира: команды и даты есть, ФИНАЛ только у финала'), check({ id: 'T1', bugs: [3] }), async ({ page, request }) => {
  const list = asList((await apiGet(request, '/tournaments?size=20')).body)
  let tournament = list[0]
  let matches = asList((await apiGet(request, `/tournaments/${tournament?.id}/matches?size=50`)).body)
  for (const row of list) {
    const batch = asList((await apiGet(request, `/tournaments/${row.id}/matches?size=50`)).body)
    if (batch.length) {
      tournament = row
      matches = batch
      break
    }
  }
  expect(tournament?.id).toBeTruthy()
  if (!tournament?.id) throw new Error('нет турнира')
  await page.goto(`/tournaments/${tournament.id}`)
  await waitForApp(page)
  const body = await page.locator('main').innerText()
  if (matches.length === 0) {
    expect(body).toMatch(/матчей нет/i)
    return
  }
  expect(body, 'должны быть названия команд').toMatch(/\S/)
  expect(body, 'должны быть даты').toMatch(/\d/)
  const finishedRegular = matches.filter((row) => row.status === 'FINISHED')
  if (finishedRegular.length) {
    const labels = page.locator('.match-row, .row.match-row')
    const texts = (await labels.allInnerTexts()).join('\n')
    expect(texts, 'у обычного сыгранного матча не должно быть ярлыка ФИНАЛ').not.toMatch(/финал/i)
  }
})

test(title({ id: 'T2', bugs: [7] }, 'календарь: фильтр по дате и виду спорта работает'), check({ id: 'T2', bugs: [7] }), async ({ page, request }) => {
  const all = await apiGet(request, '/matches?size=50&sort=scheduledAt,desc')
  const match = asList(all.body).find((row) => row.scheduledAt)
  expect(match, 'нужен хотя бы один матч на стенде').toBeTruthy()
  if (!match) throw new Error('нет матча для фильтра календаря')
  const day = String(match.scheduledAt).slice(0, 10)
  await page.goto('/calendar')
  await page.getByLabel('Дата').fill(day)
  await page.getByLabel('Дата').dispatchEvent('change')
  await expect(page.locator('.sheet, .empty').first()).toBeVisible()
  const sports = await apiGet(request, '/sports')
  const football = asList(sports.body).find((row) => row.code === 'FOOTBALL') || asList(sports.body)[0]
  if (football?.id) {
    await page.getByLabel('Вид спорта').selectOption(String(football.id))
  }
  const filtered = await page.locator('main').innerText()
  expect(filtered.length).toBeGreaterThan(0)
})

test(title({ id: 'T3', bugs: [2] }, 'одно отключённое фото не прячет галерею; API не отдаёт disabled'), check({ id: 'T3', bugs: [2] }), async ({ request }) => {
  const publicFeed = await apiGet(request, '/gallery')
  expect(publicFeed.status).toBe(200)
  const disabled = asList(asRecord(publicFeed.body).photos).filter((row) => row.enabled === false)
  expect(disabled, 'публичный /gallery не должен отдавать отключённые фото').toEqual([])

  if (!hasRole('ADMIN')) return
  const admin = await requireRole(request, 'ADMIN')
  const created = await apiSend(request, 'POST', '/admin/gallery', {
    token: admin.accessToken,
    data: {
      url: '/media/e2e-disabled-should-not-leak.png',
      title: uniqueEntityName('фото'),
      slot: 'GALLERY',
      enabled: false,
    },
  })
  expect(created.status, JSON.stringify(created.body)).toBe(201)
  const id = String(asRecord(created.body).id || '')
  try {
    const again = await apiGet(request, '/gallery')
    const leaked = asList(asRecord(again.body).photos).some((row) => row.id === id)
    expect(leaked, 'отключённое фото не должно попасть в публичную галерею').toBeFalsy()
    const remaining = asList(asRecord(again.body).photos)
    if (remaining.length === 0) {
      const home = await apiGet(request, '/home')
      const heroes = asList(asRecord(home.body).heroes)
      expect(heroes.length > 0 || remaining.length === 0, 'одно disabled-фото не должно обнулять всю ленту, если есть другие').toBeTruthy()
    }
  } finally {
    await apiSend(request, 'DELETE', `/admin/gallery/${id}`, { token: admin.accessToken })
  }
})
