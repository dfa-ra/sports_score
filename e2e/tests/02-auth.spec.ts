import { expect, test } from '@playwright/test'
import { apiSend, errorMessage } from '../src/api'
import { fanSession, registerFan } from '../src/accounts'
import { check, title } from '../src/check'
import { generatedPassword, uniqueEmail } from '../src/env'
import { fillRegisterFan, loginViaForm, russianError, waitForApp } from '../src/ui'

test(title({ id: 'R1' }, 'регистрация болельщика через форму → вход и профиль «Болельщик»'), check({ id: 'R1' }), async ({ page }) => {
  const email = uniqueEmail('form-fan')
  const password = generatedPassword()
  await fillRegisterFan(page, { firstName: 'Егор', lastName: 'Болельщиков', email, password })
  await page.getByRole('button', { name: 'Болельщик' }).click()
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expect(page).toHaveURL(/\/$|\/profile/, { timeout: 20_000 })
  await page.goto('/profile')
  await expect(page.getByText(/болельщик/i)).toBeVisible()
})

test(title({ id: 'R2' }, 'регистрация игрока: без фото — русская ошибка; с фото — успех'), check({ id: 'R2' }), async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Имя').fill('Егор')
  await page.getByLabel('Фамилия').fill('Игроков')
  await page.getByLabel('Email').fill(uniqueEmail('form-player'))
  await page.getByLabel('Пароль').fill(generatedPassword())
  await page.getByRole('button', { name: 'Игрок' }).click()
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  const error = page.locator('.form-error')
  await expect(error).toBeVisible()
  const message = await error.innerText()
  expect(russianError(message), `ошибка без фото должна быть на русском: «${message}»`).toBeTruthy()
  expect(message.toLowerCase()).toMatch(/фото/)

  await page.locator('input[type="file"]').setInputFiles('fixtures/pixel.png')
  await expect(page.getByText(/фото загружено/i)).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expect(page).toHaveURL(/\/$|\/profile/, { timeout: 20_000 })
})

test(title({ id: 'R3', bugs: [10, 11, 12, 13, 14] }, 'дубль email → ошибка на русском'), check({ id: 'R3', bugs: [10, 11, 12, 13, 14] }), async ({ page, request }) => {
  const fan = await registerFan(request, 'dup')
  const again = await apiSend(request, 'POST', '/auth/register', {
    data: {
      email: fan.email,
      password: generatedPassword(),
      firstName: 'Егор',
      lastName: 'Дубль',
      roles: ['FAN'],
      role: 'FAN',
    },
  })
  expect(again.status, 'повтор email не должен проходить').toBeGreaterThanOrEqual(400)
  expect(russianError(errorMessage(again.body)), `API: «${errorMessage(again.body)}» должно быть по-русски`).toBeTruthy()

  await fillRegisterFan(page, {
    firstName: 'Егор',
    lastName: 'Дубль',
    email: fan.email,
    password: generatedPassword(),
  })
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  const ui = page.locator('.form-error')
  await expect(ui).toBeVisible()
  expect(russianError(await ui.innerText()), `форма: «${await ui.innerText()}»`).toBeTruthy()
})

test(title({ id: 'R4', bugs: [10, 11, 12, 13, 14] }, 'пустые и кривые поля — сообщения на русском, не native English'), check({ id: 'R4', bugs: [10, 11, 12, 13, 14] }), async ({ page }) => {
  await page.goto('/register')
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expect(page.getByText(/please fill out this field/i)).toHaveCount(0)
  await expect(page.getByText(/please include an '@'/i)).toHaveCount(0)
  const native = await page.locator('input:invalid').count()
  const formError = page.locator('.form-error')
  if (await formError.count()) {
    expect(russianError(await formError.innerText())).toBeTruthy()
  } else {
    expect(native, 'пустые поля не должны оставаться на нативной английской валидации без русского текста').toBe(0)
  }

  await page.getByLabel('Имя').fill('Егор')
  await page.getByLabel('Фамилия').fill('Кривой')
  await page.getByLabel('Email').fill('not-an-email')
  await page.getByLabel('Пароль').fill('short')
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await expect(page.getByText(/please include an '@'|please lengthen|please fill/i)).toHaveCount(0)
})

test(title({ id: 'R5', bugs: [10, 11, 12, 13, 14] }, 'на отправке есть индикатор и кнопка блокируется'), check({ id: 'R5', bugs: [10, 11, 12, 13, 14] }), async ({ page }) => {
  await fillRegisterFan(page, {
    firstName: 'Егор',
    lastName: 'Индикатор',
    email: uniqueEmail('pending'),
    password: generatedPassword(),
  })
  const submit = page.getByRole('button', { name: /создать аккаунт|создаём/i })
  const click = submit.click()
  await expect(submit).toBeDisabled()
  await expect(page.getByText(/создаём/i)).toBeVisible()
  await click
})

test(title({ id: 'R6', bugs: [10, 11, 12, 13, 14] }, 'после заявки на роль профиль не показывает «ЗРИТЕЛЬ»'), check({ id: 'R6', bugs: [10, 11, 12, 13, 14] }), async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Имя').fill('Егор')
  await page.getByLabel('Фамилия').fill('Заявитель')
  await page.getByLabel('Email').fill(uniqueEmail('role-request'))
  await page.getByLabel('Пароль').fill(generatedPassword())
  await page.getByRole('button', { name: 'Игрок' }).click()
  await page.locator('input[type="file"]').setInputFiles('fixtures/pixel.png')
  await expect(page.getByText(/фото загружено/i)).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await page.goto('/profile')
  await waitForApp(page)
  const chips = (await page.locator('.badge').allInnerTexts()).join(' ')
  expect(chips.trim(), 'в профиле должна быть подпись роли').not.toBe('')
  expect(chips, `после заявки на игрока не должно быть «Зритель/ЗРИТЕЛЬ»: ${chips}`).not.toMatch(/зритель/i)
})

test(title({ id: 'R7' }, 'неверный пароль — ошибка; верный — вход; logout чистит сессию'), check({ id: 'R7' }), async ({ page, request }) => {
  const fan = await fanSession(request)
  await loginViaForm(page, fan.email, 'WrongPass1')
  await expect(page.locator('.form-error')).toBeVisible()
  await loginViaForm(page, fan.email, fan.password)
  await expect(page).not.toHaveURL(/\/login/)
  await page.goto('/profile')
  await waitForApp(page)
  await page.locator('main').getByRole('button', { name: 'Выйти' }).click()
  await expect(page.getByRole('link', { name: /войти/i }).first()).toBeVisible({ timeout: 15_000 })
  await page.goto('/profile')
  await expect(page).toHaveURL(/\/login/)
})

test(title({ id: 'R8' }, 'закрытые страницы без входа ведут на /login'), check({ id: 'R8' }), async ({ page }) => {
  for (const path of ['/admin', '/referee', '/analyst', '/my-team', '/profile']) {
    await page.goto(path)
    await expect(page, path).toHaveURL(new RegExp(`/login`))
  }
})

test(title({ id: 'R9' }, 'схема /auth/register совпадает с docs/api.md'), check({ id: 'R9' }), async ({ request }) => {
  const documented = await apiSend(request, 'POST', '/auth/register', {
    data: {
      email: uniqueEmail('docs-schema'),
      password: 'Str0ngPass!',
      accountType: 'FAN',
    },
  })
  expect(
    documented.status,
    `docs/api.md описывает {email,password,accountType}. Ответ ${documented.status} ${JSON.stringify(documented.body)}`,
  ).toBe(201)
})
