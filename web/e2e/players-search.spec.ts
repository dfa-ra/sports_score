import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

type Person = {
  id: string
  firstName: string
  lastName: string
  tournamentId: string
}

const people: Person[] = [
  { id: 'orlov', firstName: 'Илья', lastName: 'Орлов', tournamentId: 'liga' },
  { id: 'tolyan', firstName: 'Толян', lastName: 'Игроков', tournamentId: 'cup' },
]

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function nameMatches(person: Person, raw: string) {
  const needle = raw.replace(/\s+/g, ' ').trim().toLocaleLowerCase('ru')
  if (needle.length < 2) return false
  const first = person.firstName.toLocaleLowerCase('ru')
  const last = person.lastName.toLocaleLowerCase('ru')
  return needle.split(' ').every((token) => first.includes(token) || last.includes(token))
}

function boardRows(tournamentId: string | null) {
  return people
    .filter((person) => !tournamentId || person.tournamentId === tournamentId)
    .map((person) => ({
      playerId: person.id,
      firstName: person.firstName,
      lastName: person.lastName,
      goals: person.id === 'orlov' ? 4 : 1,
      assists: person.id === 'tolyan' ? 3 : 1,
      appearances: 6,
      teamId: person.tournamentId === 'liga' ? 'ft' : 'pu',
    }))
}

async function stamp(page: Page) {
  await page.evaluate(() => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'margin:12px 8px 4px;display:inline-block;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  })
}

async function shot(page: Page, name: string) {
  mkdirSync(OUT, { recursive: true })
  await page.evaluate(() => document.fonts.ready)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('player search finds a full name, a partial name, other case, and extra spaces', async ({ page }) => {
  const calls: { q: string; page: number; size: number; tournamentId: string }[] = []
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      return json(route, { scorers: boardRows(url.searchParams.get('tournamentId')), assists: boardRows(url.searchParams.get('tournamentId')) })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, {
        content: [
          { id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' },
          { id: 'cup', name: 'Кубок кампуса', status: 'ACTIVE' },
          { id: 'draft', name: 'Скрытый черновик', status: 'DRAFT' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/teams') {
      return json(route, {
        content: [
          { id: 'ft', name: 'Физтех', logoUrl: '' },
          { id: 'pu', name: 'Пумы', logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/players') {
      const q = url.searchParams.get('q') || ''
      const tournamentId = url.searchParams.get('tournamentId') || ''
      const pageNumber = Number(url.searchParams.get('page') || '0')
      const size = Number(url.searchParams.get('size') || '10')
      calls.push({ q, page: pageNumber, size, tournamentId })
      const matched = people.filter((person) => nameMatches(person, q) && (!tournamentId || person.tournamentId === tournamentId))
      const start = pageNumber * size
      return json(route, {
        content: matched.slice(start, start + size).map((person) => ({
          id: person.id,
          firstName: person.firstName,
          lastName: person.lastName,
        })),
        totalElements: matched.length,
        page: pageNumber,
        size,
      })
    }
    if (path === '/api/v1/players/orlov/card') {
      return json(route, {
        id: 'orlov',
        firstName: 'Илья',
        lastName: 'Орлов',
        statistics: { goals: 4 },
        matchHistory: [],
        team: { id: 'ft', name: 'Физтех', logoUrl: '' },
      })
    }
    if (path === '/api/v1/players/tolyan/card') {
      return json(route, {
        id: 'tolyan',
        firstName: 'Толян',
        lastName: 'Игроков',
        statistics: { goals: 1 },
        matchHistory: [],
      })
    }
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  await expect(page.getByRole('link', { name: 'Орлов Илья', exact: true }).first()).toBeVisible()

  await page.getByLabel('Поиск игрока').fill('  орлов   ИЛЬЯ ')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  await expect(page.getByText('Игроков с таким именем нет')).toHaveCount(0)
  await expect(page.getByText('Игроки не загрузились')).toHaveCount(0)
  await expect(page.getByText('Найдено · 1')).toBeVisible()
  expect(calls.at(-1)).toEqual({ q: 'орлов ИЛЬЯ', page: 0, size: 10, tournamentId: '' })

  await page.getByLabel('Поиск игрока').fill('Илья Орлов')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  expect(calls.at(-1)?.q).toBe('Илья Орлов')

  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  expect(calls.at(-1)).toEqual({ q: 'Орлов Илья', page: 0, size: 10, tournamentId: '' })

  await page.getByLabel('Поиск игрока').fill('Орл')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  await expect(page.getByText('Игроков Толян')).toHaveCount(0)

  await page.getByLabel('Поиск игрока').fill('  игроков    толян ')
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toBeVisible()
  await expect(page.getByText('Орлов Илья')).toHaveCount(0)
  expect(calls.at(-1)).toEqual({ q: 'игроков толян', page: 0, size: 10, tournamentId: '' })

  await page.getByLabel('Поиск игрока').fill('Игроков Толян')
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toBeVisible()
  expect(calls.at(-1)?.q).toBe('Игроков Толян')

  await page.getByLabel('Поиск игрока').fill('ТОЛЯН')
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toBeVisible()

  await page.getByLabel('Поиск игрока').fill('Игрок')
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toBeVisible()

  await page.getByLabel('Поиск игрока').fill('Хрюков')
  await expect(page.getByText('Игроков с таким именем нет')).toBeVisible()
  await expect(page.getByText('Проверьте написание')).toBeVisible()
  await expect(page.getByText('Игроки не загрузились')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Повторить' })).toHaveCount(0)

  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await page.getByRole('link', { name: /Орлов Илья/ }).click()
  await expect(page).toHaveURL(/\/players\/orlov$/)
  await expect(page.getByRole('heading', { name: 'Орлов Илья', exact: true })).toBeVisible()

  await page.goto('/players')
  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  await page.getByRole('button', { name: 'Очистить' }).click()
  await expect(page.getByLabel('Поиск игрока')).toHaveValue('')
  await expect(page.getByText('Игроков с таким именем нет')).toHaveCount(0)
  await expect(page.locator('.hit')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Бомбардиры' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Орлов Илья', exact: true }).first()).toBeVisible()
  await shot(page, 'q2-players-search-name-1440')
})

test('player search stays inside the active tournament filter', async ({ page }) => {
  const calls: { q: string; tournamentId: string; size: number }[] = []
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      const tournamentId = url.searchParams.get('tournamentId')
      const rows = boardRows(tournamentId)
      return json(route, { scorers: rows, assists: rows })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, {
        content: [
          { id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' },
          { id: 'cup', name: 'Кубок кампуса', status: 'ACTIVE' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/players') {
      const q = url.searchParams.get('q') || ''
      const tournamentId = url.searchParams.get('tournamentId') || ''
      const size = Number(url.searchParams.get('size') || '10')
      calls.push({ q, tournamentId, size })
      const matched = people.filter((person) => nameMatches(person, q) && (!tournamentId || person.tournamentId === tournamentId))
      return json(route, {
        content: matched.map((person) => ({
          id: person.id,
          firstName: person.firstName,
          lastName: person.lastName,
        })),
        totalElements: matched.length,
        page: 0,
        size,
      })
    }
    if (path.endsWith('/card')) return json(route, {})
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  await page.getByRole('button', { name: 'Лига ИТМО', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Орлов Илья', exact: true }).first()).toBeVisible()
  await expect(page.getByText('Игроков Толян')).toHaveCount(0)

  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  await expect(page.getByText('Игроков Толян')).toHaveCount(0)
  expect(calls.at(-1)).toEqual({ q: 'Орлов Илья', tournamentId: 'liga', size: 10 })

  await page.getByLabel('Поиск игрока').fill('Игроков Толян')
  await expect(page.getByText('Игроков с таким именем нет')).toBeVisible()
  await expect(page.getByText('Игроки не загрузились')).toHaveCount(0)
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toHaveCount(0)
  expect(calls.at(-1)).toEqual({ q: 'Игроков Толян', tournamentId: 'liga', size: 10 })

  await page.getByRole('button', { name: 'Очистить' }).click()
  await expect(page.getByRole('heading', { name: 'Бомбардиры' })).toBeVisible()
  await page.getByRole('button', { name: 'Кубок кампуса', exact: true }).click()
  await page.getByLabel('Поиск игрока').fill('Игроков Толян')
  await expect(page.getByRole('link', { name: /Игроков Толян/ })).toBeVisible()
  await expect(page.getByText('Орлов Илья')).toHaveCount(0)
  expect(calls.at(-1)).toEqual({ q: 'Игроков Толян', tournamentId: 'cup', size: 10 })

  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await expect(page.getByText('Игроков с таким именем нет')).toBeVisible()
  expect(calls.at(-1)).toEqual({ q: 'Орлов Илья', tournamentId: 'cup', size: 10 })

  await page.getByRole('button', { name: 'Очистить' }).click()
  await page.getByRole('button', { name: 'Все турниры' }).click()
  await page.getByLabel('Поиск игрока').fill('Орлов Илья')
  await expect(page.getByRole('link', { name: /Орлов Илья/ })).toBeVisible()
  expect(calls.at(-1)).toEqual({ q: 'Орлов Илья', tournamentId: '', size: 10 })
})
