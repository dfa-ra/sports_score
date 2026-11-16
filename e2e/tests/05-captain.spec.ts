import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, errorMessage } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { uniqueEntityName } from '../src/env'
import { russianError } from '../src/ui'

test(title({ id: 'C1', bugs: [26, 27, 28, 29] }, 'капитан без команды видит способ создать/получить её или подсказку'), check({ id: 'C1', bugs: [26, 27, 28, 29] }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status === 200, 'у этого капитана уже есть команда — C1 про без команды')
  await injectSession(page, captain)
  await page.goto('/my-team')
  const text = await page.locator('main').innerText()
  expect(text, text).toMatch(/команд|созда|назнач|админ|подсказ/i)
})

test(title({ id: 'C2' }, '/my-team с командой: карточка, состав, календарь'), check({ id: 'C2' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status !== 200, 'нужна команда у капитана')
  await injectSession(page, captain)
  await page.goto('/my-team')
  await expect(page.getByRole('heading', { name: 'Моя команда' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Карточка' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Состав' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Календарь команды' })).toBeVisible()
})

test(title({ id: 'C3' }, 'добавить зарегистрированного игрока из поиска; есть подсказка'), check({ id: 'C3' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const playerReason = skipWithout('PLAYER')
  test.skip(!!playerReason, playerReason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const player = await requireRole(request, 'PLAYER')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status !== 200, 'нужна команда у капитана')
  const teamId = String((mine.body as { id: string }).id)
  const me = await apiGet(request, '/players/me', player.accessToken)
  expect(me.status).toBe(200)
  const playerId = String((me.body as { id?: string }).id || '')
  const lastName = String((me.body as { lastName?: string }).lastName || player.user.lastName || '')
  const firstName = String((me.body as { firstName?: string }).firstName || player.user.firstName || '')
  const query = lastName.length >= 2 ? lastName : firstName
  expect(query.length, 'у игрока должна быть фамилия или имя ≥ 2 символов').toBeGreaterThanOrEqual(2)
  const membersBefore = asList((await apiGet(request, `/teams/${teamId}/members`)).body)
  const already = membersBefore.some((row) => String(row.playerId) === playerId)
  if (already) {
    await apiSend(request, 'DELETE', `/teams/${teamId}/members/${playerId}`, { token: captain.accessToken })
  }
  await injectSession(page, captain)
  await page.goto('/my-team')
  await expect(page.getByRole('heading', { name: 'Моя команда' })).toBeVisible()
  const search = page.getByPlaceholder('Фамилия или имя')
  await search.fill(query)
  const hint = page.getByRole('button', { name: new RegExp(`${lastName}|${firstName}`, 'i') }).first()
  await expect(hint).toBeVisible()
  await hint.click()
  await page.getByRole('button', { name: 'Добавить игрока' }).click()
  await expect(page.getByText(/игрок добавлен/i)).toBeVisible()
  const members = asList((await apiGet(request, `/teams/${teamId}/members`)).body)
  expect(members.some((row) => String(row.playerId) === playerId)).toBeTruthy()
  await expect(
    page.getByText(/только зарегистрир|после подтверждения|из поиска/i),
    'подсказка, что в состав берутся только зарегистрированные игроки',
  ).toBeVisible()
  await apiSend(request, 'DELETE', `/teams/${teamId}/members/${playerId}`, {
    token: captain.accessToken,
  })
})

test(title({ id: 'C4' }, '«Убрать» спрашивает подтверждение'), check({ id: 'C4' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status !== 200, 'нужна команда у капитана')
  await injectSession(page, captain)
  await page.goto('/my-team')
  const remove = page.getByRole('button', { name: 'Убрать' }).first()
  test.skip(await remove.count() === 0, 'в составе нет игрока кроме капитана')
  const dialogs: string[] = []
  page.on('dialog', async (dialog: { message: () => string; dismiss: () => Promise<void> }) => {
    dialogs.push(dialog.message())
    await dialog.dismiss()
  })
  await remove.click()
  expect(dialogs.length, 'кнопка «Убрать» должна спросить подтверждение').toBeGreaterThan(0)
})

test(title({ id: 'C5' }, 'загрузка логотипа отображается на странице команды'), check({ id: 'C5' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status !== 200, 'нужна команда у капитана')
  const teamId = String((mine.body as { id: string }).id)
  await injectSession(page, captain)
  await page.goto('/my-team')
  await page.locator('input[type="file"]').setInputFiles('fixtures/pixel.png')
  await expect(page.getByText(/логотип обновлён/i)).toBeVisible({ timeout: 15_000 })
  const team = await apiGet(request, `/teams/${teamId}`)
  const logo = String((team.body as { logoUrl?: string }).logoUrl || '')
  expect(logo, 'у команды должен появиться logoUrl').toMatch(/\/media\//)
  await page.goto(`/teams/${teamId}`)
  await expect(page.locator(`img[src="${logo}"]`).first()).toBeVisible()
})

test(title({ id: 'C6' }, 'название: пустое — русская ошибка; >200 — сообщение, не обрезка; дубль — ошибка'), check({ id: 'C6' }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  const mine = await apiGet(request, '/teams/mine', captain.accessToken)
  test.skip(mine.status !== 200, 'нужна команда у капитана')
  const teamId = String((mine.body as { id: string }).id)
  const original = String((mine.body as { name?: string }).name || '')
  await injectSession(page, captain)
  await page.goto('/my-team')
  const name = page.getByLabel('Название')
  await name.fill('')
  await page.getByRole('button', { name: 'Сохранить карточку' }).click()
  const emptyError = page.locator('.form-error')
  if (await emptyError.count()) {
    expect(russianError(await emptyError.innerText())).toBeTruthy()
  } else {
    await expect(page.getByText(/please fill/i)).toHaveCount(0)
    expect(false, 'пустое название должно давать русскую ошибку, не native required').toBeTruthy()
  }

  const long = `E2E ${'я'.repeat(210)}`
  await name.evaluate((el: HTMLInputElement, value: string) => {
    el.removeAttribute('maxlength')
    el.value = value
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }, long)
  await page.getByRole('button', { name: 'Сохранить карточку' }).click()
  const after = await apiGet(request, `/teams/${teamId}`)
  const saved = String((after.body as { name?: string }).name || '')
  expect(saved.length, 'название >200 не должно молча обрезаться без сообщения').toBeLessThanOrEqual(200)
  await expect(page.locator('.form-error')).toBeVisible()

  const other = asList((await apiGet(request, '/teams?size=20')).body).find((row) => row.id !== teamId)
  if (other?.name) {
    const dup = await apiSend(request, 'PUT', `/teams/${teamId}`, {
      token: captain.accessToken,
      data: { name: other.name },
    })
    expect(dup.status, `дубль названия → ${dup.status} ${errorMessage(dup.body)}`).toBeGreaterThanOrEqual(400)
  }

  await apiSend(request, 'PUT', `/teams/${teamId}`, {
    token: captain.accessToken,
    data: { name: original },
  })
})

test(title({ id: 'C7', bugs: [26, 27, 28, 29] }, 'подпись роли в профиле «Капитан»'), check({ id: 'C7', bugs: [26, 27, 28, 29] }), async ({ page, request }) => {
  const reason = skipWithout('CAPTAIN')
  test.skip(!!reason, reason || '')
  const captain = await requireRole(request, 'CAPTAIN')
  await injectSession(page, captain)
  await page.goto('/profile')
  await expect(page.getByText('Капитан')).toBeVisible()
})
