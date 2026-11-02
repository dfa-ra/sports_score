import { expect, type Page } from '@playwright/test'

export async function waitForApp(page: Page) {
  await page.locator('#app').waitFor({ state: 'visible' })
  await page.waitForFunction(() => {
    const main = document.querySelector('main')
    return !!main && (main.textContent || '').trim().length > 0
  })
}

export async function open(page: Page, path: string) {
  const errors: string[] = []
  const failed5xx: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 500) {
      failed5xx.push(`${response.status()} ${response.url()}`)
    }
  })
  const response = await page.goto(path, { waitUntil: 'domcontentloaded' })
  await waitForApp(page)
  return { response, errors, failed5xx }
}

export async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const root = document.documentElement
    return root.scrollWidth > root.clientWidth + 2
  })
}

export function russianError(text: string): boolean {
  return /[А-Яа-яЁё]/.test(text)
}

export async function visibleText(page: Page, pattern: RegExp | string) {
  const locator = typeof pattern === 'string' ? page.getByText(pattern, { exact: false }) : page.getByText(pattern)
  await expect(locator.first()).toBeVisible()
}

export async function fillRegisterFan(page: Page, data: {
  firstName: string
  lastName: string
  email: string
  password: string
}) {
  await page.goto('/register')
  await page.getByLabel('Имя').fill(data.firstName)
  await page.getByLabel('Фамилия').fill(data.lastName)
  await page.getByLabel('Email').fill(data.email)
  await page.getByLabel('Пароль').fill(data.password)
}

export async function loginViaForm(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль').fill(password)
  await page.getByRole('button', { name: 'Войти' }).click()
}
