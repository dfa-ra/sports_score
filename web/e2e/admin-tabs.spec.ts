import { expect, test, type Page } from '@playwright/test'

function token() {
  const exp = Math.floor(Date.now() / 1000) + 60 * 60
  const payload = Buffer.from(JSON.stringify({ exp })).toString('base64url')
  return `e30.${payload}.sig`
}

function user(role: string) {
  return {
    id: 'u1',
    email: role === 'ADMIN' ? 'admin@liga.test' : 'player@liga.test',
    role,
    enabled: true,
    firstName: 'Илья',
    lastName: 'Орлов',
    roles: [{ role, status: 'APPROVED' }],
  }
}

async function signIn(page: Page, role: string) {
  const account = user(role)
  const access = token()
  await page.addInitScript(({ access, account }) => {
    const raw = JSON.stringify(account)
    localStorage.setItem('sl_access', access)
    localStorage.setItem('sl_user', raw)
    sessionStorage.setItem('sl_access', access)
    sessionStorage.setItem('sl_user', raw)
  }, { access, account })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/v1/auth/me') return json(account)
    if (path === '/api/v1/admin/users') return json({ content: [], totalPages: 0 })
    if (path === '/api/v1/admin/referees') return json({ content: [], totalPages: 0 })
    if (path === '/api/v1/admin/role-requests') return json([])
    if (path === '/api/v1/admin/gallery') return json({ photos: [], vkAlbumUrl: '' })
    if (path === '/api/v1/tournaments' || path === '/api/v1/matches' || path === '/api/v1/teams') {
      return json({ content: [], totalPages: 0 })
    }
    return json({ message: 'no' }, 404)
  })
}

const tabs = [
  { path: '/admin/users', tab: 'users', heading: 'Пользователи' },
  { path: '/admin/referees', tab: 'referees', heading: 'Судьи' },
  { path: '/admin/role-requests', tab: 'users', heading: 'Пользователи' },
  { path: '/admin/gallery', tab: 'gallery', heading: 'Главная: слайды и фото' },
  { path: '/admin/gallery/vk-album', tab: 'gallery', heading: 'Главная: слайды и фото' },
]

test('an admin bookmark opens the matching /admin tab', async ({ page }) => {
  await signIn(page, 'ADMIN')
  for (const item of tabs) {
    await page.goto(item.path)
    await expect(page).toHaveURL(new RegExp(`/admin\\?tab=${item.tab}$`))
    await expect(page.getByRole('heading', { name: item.heading })).toBeVisible()
    if (item.path.includes('vk-album')) await expect(page.getByText('Альбом ВК')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Не найдено' })).toHaveCount(0)
    await expect(page.locator('.tabs button.on')).toHaveText(item.tab === 'users' ? 'Пользователи' : item.tab === 'referees' ? 'Судьи' : 'Фото')
  }
})

test('a signed-in non-admin gets the same screen as /admin', async ({ page }) => {
  await signIn(page, 'PLAYER')
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible()
  await expect(page).toHaveURL(/\/forbidden$/)
  for (const item of tabs) {
    await page.goto(item.path)
    await expect(page.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Не найдено' })).toHaveCount(0)
    await expect(page).toHaveURL(/\/forbidden$/)
  }
})
