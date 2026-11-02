import { execSync } from 'node:child_process'
import { expect, test } from '@playwright/test'
import { check, title } from '../src/check'
import { firstPublicMatch, firstPublicTournament } from '../src/entities'
import { hasHorizontalOverflow, open, waitForApp } from '../src/ui'

const publicPages = [
  { path: '/tournaments', name: 'Турниры' },
  { path: '/teams', name: 'Команды' },
  { path: '/live', name: 'Live' },
  { path: '/calendar', name: 'Календарь' },
  { path: '/gallery', name: 'Галерея' },
]

test(title({ id: 'S1' }, 'главная открывается, 200, нет ошибок консоли и 5xx'), check({ id: 'S1' }), async ({ page }) => {
  const { response, errors, failed5xx } = await open(page, '/')
  expect(response?.status(), 'главная должна отдавать 200').toBe(200)
  expect(failed5xx, `сеть не должна отдавать 5xx: ${failed5xx.join('; ')}`).toEqual([])
  expect(errors, `в консоли не должно быть исключений: ${errors.join('; ')}`).toEqual([])
  await expect(page.locator('body')).toBeVisible()
})

test(title({ id: 'S2' }, 'публичные разделы открываются с контентом или понятной пустотой'), check({ id: 'S2' }), async ({ page }) => {
  for (const item of publicPages) {
    const { response } = await open(page, item.path)
    expect(response?.status(), `${item.path} должен открываться`).toBe(200)
    const body = await page.locator('main').innerText()
    expect(body.trim().length, `${item.path}: пустой main`).toBeGreaterThan(0)
    if (item.path === '/gallery') {
      expect(body, 'S2 /gallery — отдельная страница галереи, не generic 404').not.toMatch(/такой страницы нет/i)
    }
  }
})

function resolveExpectedSha(): { sha?: string; reason?: string } {
  const fromEnv = (process.env.E2E_EXPECTED_SHA || '').trim()
  if (fromEnv) return { sha: fromEnv }
  try {
    const remote = execSync('git ls-remote origin refs/heads/main', {
      encoding: 'utf8',
      timeout: 15_000,
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim()
    const sha = remote.split(/\s+/)[0] || ''
    if (sha.length > 7) return { sha }
    return { reason: `git ls-remote origin refs/heads/main не дал SHA: «${remote}»` }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    return { reason: `нет git remote (${message.split('\n')[0]}) и нет E2E_EXPECTED_SHA` }
  }
}

test(title({ id: 'S3' }, 'в футере версия совпадает с последним коммитом main'), check({ id: 'S3' }), async ({ page }) => {
  const expected = resolveExpectedSha()
  test.skip(!expected.sha, expected.reason || 'нет ожидаемого SHA')
  const sha = expected.sha || ''
  await page.goto('/')
  await waitForApp(page)
  const footer = page.locator('.foot-ver')
  await expect(footer).toBeVisible()
  const label = (await footer.innerText()).trim()
  expect(label, `футер «${label}» должен содержать короткий SHA ${sha.slice(0, 7)}`).toContain(sha.slice(0, 7))
})

test(title({ id: 'S4', bugs: [2] }, 'HERO и галерея не показывают отключённые фото'), check({ id: 'S4', bugs: [2] }), async ({ page, request }) => {
  const feed = await request.get('/api/v1/home', { failOnStatusCode: false })
  const gallery = await request.get('/api/v1/gallery', { failOnStatusCode: false })
  expect(feed.ok()).toBeTruthy()
  expect(gallery.ok()).toBeTruthy()
  const homeBody = await feed.json() as {
    heroes?: { enabled?: boolean; url?: string }[]
    stories?: { enabled?: boolean; url?: string }[]
    photos?: { enabled?: boolean; url?: string }[]
  }
  const galleryBody = await gallery.json() as { photos?: { enabled?: boolean; url?: string; title?: string }[] }
  const slides = [
    ...(homeBody.heroes ?? []),
    ...(homeBody.stories ?? []),
    ...(homeBody.photos ?? []),
    ...(galleryBody.photos ?? []),
  ]
  const disabled = slides.filter((item) => item.enabled === false)
  expect(disabled, 'публичные ленты не должны отдавать enabled=false').toEqual([])
  await page.goto('/')
  const urls = slides.map((item) => item.url).filter(Boolean) as string[]
  for (const url of urls) {
    const img = page.locator(`img[src="${url}"]`)
    if (await img.count()) {
      await expect(img.first()).toBeVisible()
    }
  }
})

test(title({ id: 'S5' }, 'мобильная вёрстка 375×812 без горизонтального скролла'), check({ id: 'S5' }), async ({ page, request }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  const tournament = await firstPublicTournament(request)
  const match = await firstPublicMatch(request)
  const paths = [
    '/',
    tournament?.id ? `/tournaments/${tournament.id}` : '/tournaments',
    match?.id ? `/matches/${match.id}` : '/calendar',
  ]
  for (const path of paths) {
    await page.goto(path)
    await waitForApp(page)
    expect(await hasHorizontalOverflow(page), `${path} не должен горизонтально скроллиться на 375×812`).toBeFalsy()
  }
  await page.goto('/')
  await expect(page.locator('nav.dock')).toBeVisible()
  expect(await hasHorizontalOverflow(page), 'мобильное меню не должно давать горизонтальный скролл').toBeFalsy()
})
