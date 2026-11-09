import { expect, test } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const WIDTHS = [320, 375, 1440] as const

const USER = {
  id: 'u1',
  email: 'anna@itmo.ru',
  role: 'FAN',
  enabled: true,
  firstName: 'Анна',
  lastName: 'Ким',
  roles: [{ role: 'FAN', status: 'APPROVED' }],
}

function futureAccess() {
  const exp = Math.floor(Date.now() / 1000) + 60 * 60
  const payload = Buffer.from(JSON.stringify({ exp })).toString('base64url')
  return `e30.${payload}.x`
}

function readHeader() {
  const box = (el: Element | null) => {
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.x, y: r.y, w: r.width, h: r.height, right: r.right, left: r.left, top: r.top, bottom: r.bottom }
  }
  const session = document.querySelector('.session')
  const rule = document.querySelector('.session-rule')
  const login = document.querySelector('.login-pill:not(.auth-pending)')
  const pending = document.querySelector('.auth-pending')
  const menuEl = document.querySelector('.menu-button')
  const menu = menuEl && menuEl.getBoundingClientRect().width > 0 ? menuEl : null
  const profile = document.querySelector('.profile-button')
  const avatar = document.querySelector('.profile-button .player-avatar')
  const brand = document.querySelector('.brand-name') as HTMLElement | null
  const icon = document.querySelector('.menu-icon')
  const sessionStyle = session ? getComputedStyle(session) : null
  const loginStyle = login ? getComputedStyle(login) : null
  const ruleStyle = rule ? getComputedStyle(rule) : null
  const bars = icon
    ? [...icon.querySelectorAll('span')].map((bar) => {
      const style = getComputedStyle(bar)
      return { w: style.width, h: style.height, radius: style.borderRadius }
    })
    : []
  const before = (el: Element | null) => (el ? getComputedStyle(el, '::before').height : null)
  const sessionBox = box(session)
  const ruleBox = box(rule)
  return {
    scroll: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    session: sessionBox,
    sessionRadius: sessionStyle?.borderRadius ?? null,
    sessionBorder: sessionStyle ? { w: sessionStyle.borderTopWidth, c: sessionStyle.borderTopColor } : null,
    rule: ruleBox,
    ruleColor: ruleStyle?.backgroundColor ?? null,
    ruleCentered: sessionBox && ruleBox ? Math.abs((ruleBox.top + ruleBox.bottom) / 2 - (sessionBox.top + sessionBox.bottom) / 2) : null,
    login: box(login),
    loginRadius: loginStyle?.borderRadius ?? null,
    loginBorder: loginStyle ? { w: loginStyle.borderTopWidth, c: loginStyle.borderTopColor } : null,
    loginPad: loginStyle ? { l: loginStyle.paddingLeft, r: loginStyle.paddingRight } : null,
    loginFont: loginStyle ? { family: loginStyle.fontFamily, size: loginStyle.fontSize, weight: loginStyle.fontWeight } : null,
    pending: box(pending),
    menu: box(menu),
    menuHit: before(menu),
    loginHit: before(login),
    profileHit: before(profile),
    profile: box(profile),
    avatar: box(avatar),
    expanded: menu?.getAttribute('aria-expanded') ?? null,
    label: menu?.getAttribute('aria-label') ?? null,
    glyph: menu?.textContent ?? '',
    bars,
    iconGap: icon ? getComputedStyle(icon).rowGap || getComputedStyle(icon).gap : null,
    brand: brand?.textContent ?? '',
    brandClipped: brand ? brand.scrollWidth > brand.clientWidth + 1 : null,
    brandRight: box(brand)?.right ?? null,
    rightInset: sessionBox ? window.innerWidth - sessionBox.right : null,
    nav: document.querySelector('nav.primary') ? getComputedStyle(document.querySelector('nav.primary')!).display : null,
  }
}

test('header block B at 320, 375 and 1440', async ({ page }) => {
  test.setTimeout(90_000)
  mkdirSync(OUT, { recursive: true })
  const report: Record<string, unknown> = {}

  let signedIn = false
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) {
      return signedIn ? json(USER) : json({ message: 'no' }, 401)
    }
    if (path.endsWith('/home/overview')) {
      return json({ liveMatches: [], upcomingMatches: [], recentResults: [], topScorers: [], scorersByTournament: [] })
    }
    return json({ content: [] })
  })
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: width >= 1000 ? 900 : 812 })
    await page.goto('/')
    await expect(page.locator('.chrome')).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const guest = await page.evaluate(readHeader)
    report[`guest-${width}`] = guest
    expect(guest.scroll, `scroll ${width}`).toBeLessThanOrEqual(0)
    if (width < 1024) {
      expect(guest.session?.h).toBe(40)
      expect(guest.sessionRadius).toBe('10px')
      expect(guest.sessionBorder?.w).toBe('1px')
      expect(guest.sessionBorder?.c).toBe('rgba(255, 255, 255, 0.45)')
      expect(guest.rule?.h).toBe(24)
      expect(guest.rule?.w).toBe(1)
      expect(guest.ruleCentered ?? 9).toBeLessThanOrEqual(0.5)
      expect(guest.menu?.w).toBe(44)
      expect(guest.login?.w ?? 0).toBeGreaterThanOrEqual(84)
      expect(guest.loginPad).toEqual({ l: '16px', r: '16px' })
      expect(guest.loginFont?.size).toBe('14px')
      expect(Number(guest.loginFont?.weight)).toBe(700)
      expect(guest.loginFont?.family.toLowerCase()).toContain('tektur')
      expect(guest.loginHit).toBe('44px')
      expect(guest.menuHit).toBe('44px')
      expect(guest.bars).toEqual([
        { w: '18px', h: '2px', radius: '1px' },
        { w: '18px', h: '2px', radius: '1px' },
        { w: '18px', h: '2px', radius: '1px' },
      ])
      expect(guest.iconGap).toBe('4px')
      expect(guest.glyph).not.toContain('☰')
      expect(guest.expanded).toBe('false')
      expect(guest.label).toBe('Открыть меню')
      expect(Math.abs((guest.rightInset ?? 99) - 12)).toBeLessThanOrEqual(1)
      if (width === 375) {
        expect(guest.brandClipped).toBe(false)
        expect(guest.brand).toContain('ИТМО')
        expect(guest.brandRight ?? 999).toBeLessThanOrEqual((guest.session?.left ?? 0) + 1)
      }
    } else {
      expect(guest.login?.h).toBe(40)
      expect(guest.loginRadius).toBe('10px')
      expect(guest.loginBorder?.w).toBe('1px')
      expect(guest.loginBorder?.c).toBe('rgba(255, 255, 255, 0.45)')
      expect(guest.menu).toBeNull()
      expect(guest.nav).not.toBe('none')
    }
    await page.screenshot({ path: `${OUT}/header-guest-${width}.png`, clip: { x: 0, y: 0, width, height: 220 } })

    if (width < 1024) {
      const before = guest.session?.w
      await page.locator('.menu-button').click()
      await expect(page.locator('.menu-button')).toHaveAttribute('aria-expanded', 'true')
      const open = await page.evaluate(readHeader)
      report[`open-${width}`] = open
      expect(open.session?.w).toBe(before)
      expect(open.session?.h).toBe(40)
      expect(open.expanded).toBe('true')
      expect(open.label).toBe('Закрыть меню')
      expect(open.glyph).not.toContain('☰')
      expect(open.bars).toEqual([
        { w: '14px', h: '2px', radius: '1px' },
        { w: '14px', h: '2px', radius: '1px' },
      ])
      expect(open.scroll).toBeLessThanOrEqual(0)
      await page.screenshot({ path: `${OUT}/header-open-${width}.png`, clip: { x: 0, y: 0, width, height: 420 } })
      await page.locator('.menu-button').click()
    } else {
      report[`open-${width}`] = { menuHidden: true }
      await page.screenshot({ path: `${OUT}/header-open-${width}.png`, clip: { x: 0, y: 0, width, height: 220 } })
    }
  }

  signedIn = true
  await page.addInitScript((token) => {
    localStorage.setItem('sl_access', token)
  }, futureAccess())
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: width >= 1000 ? 900 : 812 })
    await page.goto('/')
    await expect(page.locator('.profile-button')).toBeVisible()
    const closed = await page.evaluate(readHeader)
    report[`user-${width}`] = closed
    expect(closed.scroll).toBeLessThanOrEqual(0)
    expect(closed.login).toBeNull()
    if (width < 1024) {
      expect(closed.session?.h).toBe(40)
      expect(closed.profile?.w).toBe(48)
      expect(closed.avatar?.w).toBe(28)
      expect(closed.avatar?.h).toBe(28)
      expect(closed.menu?.w).toBe(44)
      expect(closed.profileHit).toBe('44px')
      const before = closed.session?.w
      await page.locator('.menu-button').click()
      const open = await page.evaluate(readHeader)
      report[`user-open-${width}`] = open
      expect(open.session?.w).toBe(before)
      expect(open.expanded).toBe('true')
      expect(open.scroll).toBeLessThanOrEqual(0)
      await page.locator('.menu-button').click()
    } else {
      expect(closed.profile?.w).toBe(40)
      expect(closed.profile?.h).toBe(40)
      expect(closed.menu).toBeNull()
    }
    await page.screenshot({ path: `${OUT}/header-user-${width}.png`, clip: { x: 0, y: 0, width, height: 220 } })
  }

  writeFileSync(`${OUT}/header-metrics.json`, JSON.stringify(report, null, 2))
})

test('auth pending keeps the header block width', async ({ page }) => {
  let hold = new Promise<void>(() => {})
  await page.addInitScript((token) => {
    localStorage.setItem('sl_access', token)
  }, futureAccess())
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path.endsWith('/auth/me')) {
      await hold
      return json(USER)
    }
    if (path.endsWith('/auth/refresh')) return json({ message: 'no' }, 401)
    if (path.endsWith('/home/overview')) {
      return json({ liveMatches: [], upcomingMatches: [], recentResults: [], topScorers: [], scorersByTournament: [] })
    }
    return json({ content: [] })
  })

  for (const width of WIDTHS) {
    let release: () => void = () => {}
    hold = new Promise<void>((resolve) => {
      release = resolve
    })
    await page.setViewportSize({ width, height: 812 })
    await page.goto('/')
    await expect(page.locator('.auth-pending')).toBeAttached()
    const first = await page.evaluate(readHeader)
    await page.waitForTimeout(150)
    const still = await page.evaluate(readHeader)
    expect(still.session?.w, `pending stable ${width}`).toBe(first.session?.w)
    expect(still.session?.x, `pending x ${width}`).toBe(first.session?.x)
    expect(still.pending?.w).toBeGreaterThan(0)
    release()
    await expect(page.locator('.profile-button')).toBeVisible()
    const user = await page.evaluate(readHeader)
    expect(user.session?.w, `pending to user ${width}`).toBe(first.session?.w)
    expect(user.session?.x, `pending x to user ${width}`).toBe(first.session?.x)
    expect(user.scroll).toBeLessThanOrEqual(0)
  }
})
