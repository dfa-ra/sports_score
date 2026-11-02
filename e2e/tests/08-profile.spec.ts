import { expect, test } from '@playwright/test'
import { apiGet, apiSend } from '../src/api'
import { fanSession, injectSession, registerFan } from '../src/accounts'
import { check, title } from '../src/check'
import { generatedPassword } from '../src/env'
import { firstPublicTeam } from '../src/entities'
import { waitForApp } from '../src/ui'

test(title({ id: 'U1' }, 'смена аватара и имени в профиле'), check({ id: 'U1' }), async ({ page, request }) => {
  const fan = await registerFan(request, 'profile')
  await injectSession(page, fan)
  await page.goto('/profile')
  await waitForApp(page)
  await page.getByRole('button', { name: 'Стать игроком' }).click()
  const form = page.locator('.sheet-form')
  await expect(form).toBeVisible()
  await form.getByLabel('Имя').fill('Егор')
  await form.getByLabel('Фамилия').fill('Профилев')
  await form.getByLabel('Как писать на майке').fill('Егор')
  await form.locator('input[type="file"]').setInputFiles('fixtures/pixel.png')
  await expect(form.getByText(/фото обновлено/i)).toBeVisible({ timeout: 15_000 })
  await form.getByLabel('Номер').fill('7')
  await form.getByLabel('Позиция').selectOption('Нападающий')
  await form.getByRole('button', { name: 'Стать игроком' }).click()
  await expect(form).toBeHidden({ timeout: 15_000 })
  await expect(page.getByRole('heading', { name: /профилев/i })).toBeVisible()
  const me = await apiGet(request, '/players/me', fan.accessToken)
  expect(me.status, JSON.stringify(me.body)).toBe(200)
  expect(String((me.body as { firstName?: string }).firstName || '')).toBe('Егор')
})

test(title({ id: 'U2' }, 'смена пароля: старый неверный — ошибка; успех — вход новым'), check({ id: 'U2' }), async ({ page, request }) => {
  const fan = await registerFan(request, 'pwd')
  await injectSession(page, fan)
  await page.goto('/profile')
  await waitForApp(page)
  const oldPassword = page.getByLabel(/старый пароль|текущий пароль/i)
  const newPassword = page.getByLabel(/новый пароль/i)
  expect(await oldPassword.count(), 'в профиле должна быть форма смены пароля со старым и новым').toBeGreaterThan(0)
  await oldPassword.fill('TotallyWrong1')
  await newPassword.fill(generatedPassword())
  await page.getByRole('button', { name: /сменить пароль|сохранить пароль/i }).click()
  await expect(page.locator('.form-error')).toBeVisible()
  const next = generatedPassword()
  await oldPassword.fill(fan.password)
  await newPassword.fill(next)
  await page.getByRole('button', { name: /сменить пароль|сохранить пароль/i }).click()
  await expect(page.getByText(/пароль.*обнов|сохран/i)).toBeVisible()
  const login = await apiSend(request, 'POST', '/auth/login', { data: { email: fan.email, password: next } })
  expect(login.status).toBe(200)
})

test(title({ id: 'U3' }, 'избранное команд/турниров сохраняется после перезахода'), check({ id: 'U3' }), async ({ page, request }) => {
  const fan = await fanSession(request)
  const team = await firstPublicTeam(request)
  expect(team?.id).toBeTruthy()
  if (!team?.id) throw new Error('нет публичной команды')
  await injectSession(page, fan)
  await page.goto(`/teams/${team.id}`)
  await waitForApp(page)
  await page.locator('button.star').first().click()
  const stored = await page.evaluate(() => localStorage.getItem('kb_favorites'))
  expect(stored, 'звёздочка должна записать избранное').toContain(String(team.id))
  await page.goto('/profile')
  await waitForApp(page)
  await page.locator('main').getByRole('button', { name: 'Выйти' }).click()
  await expect(page.getByRole('link', { name: /войти/i }).first()).toBeVisible({ timeout: 15_000 })
  await injectSession(page, fan)
  await page.goto('/profile')
  await waitForApp(page)
  await expect(page.getByRole('link', { name: new RegExp(String(team.name || ''), 'i') }).first()).toBeVisible()
})

test(title({ id: 'U4' }, 'сброс пароля по email'), check({ id: 'U4' }), async ({ page, request }) => {
  const fan = await fanSession(request)
  const forgot = await apiSend(request, 'POST', '/auth/forgot-password', { data: { email: fan.email } })
  expect(forgot.status, 'forgot-password всегда 204, даже если почта не настроена').toBe(204)
  await page.goto('/forgot-password')
  await page.getByLabel('Email').fill(fan.email)
  await page.getByRole('button', { name: 'Отправить ссылку' }).click()
  await expect(page.getByText(/отправили ссылку|если аккаунт/i)).toBeVisible()
  test.info().annotations.push({
    type: 'note',
    description: 'Письмо на dev может не уходить — проверяем форму и стабильный 204, не чтение ящика.',
  })
})
