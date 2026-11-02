import { expect, test } from '@playwright/test'
import { apiGet, apiPath, apiSend, asList, asRecord, errorMessage } from '../src/api'
import { PIXEL_PNG, registerFan, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { uniqueStamp } from '../src/env'
import { createMatch } from '../src/entities'
import { deleteMatch, ensureLivePool, prepareAssignedMatch } from '../src/pool'

const TEXT_JPG = Buffer.from('this is not a jpeg, just plain text for e2e avatar check\n')

function roleNames(body: unknown): string[] {
  const record = asRecord(body)
  const names = [String(record.role || '')]
  for (const row of asList(record.roles)) {
    names.push(String(row.role || row.name || ''))
  }
  return names.filter(Boolean)
}

function emailsIn(value: unknown): string[] {
  const found: string[] = []
  const walk = (node: unknown) => {
    if (Array.isArray(node)) {
      node.forEach(walk)
      return
    }
    if (!node || typeof node !== 'object') return
    for (const [key, item] of Object.entries(node as Record<string, unknown>)) {
      if (/email/i.test(key) && typeof item === 'string' && item.includes('@')) found.push(item)
      else walk(item)
    }
  }
  walk(value)
  return found
}

test(title({ id: 'E6' }, 'GET /matches/{id}/referees анониму не отдаёт email судей'), check({ id: 'E6' }), async ({ request }) => {
  const matches = asList((await apiGet(request, '/matches?size=50&sort=scheduledAt,desc')).body)
  let matchId = ''
  for (const row of matches) {
    const referees = await apiGet(request, `/matches/${row.id}/referees`)
    if (referees.status === 200 && asList(referees.body).length) {
      matchId = String(row.id)
      const leaked = emailsIn(referees.body)
      expect(leaked, `анониму видны email судей: ${leaked.join(', ')}`).toEqual([])
      break
    }
  }
  if (matchId) return
  const reason = skipWithout('ADMIN') || skipWithout('REFEREE')
  test.skip(!!reason, reason || 'на стенде нет матча с судьёй')
  const ctx = await prepareAssignedMatch(request)
  try {
    const referees = await apiGet(request, `/matches/${ctx.matchId}/referees`)
    expect(referees.status, `GET referees → ${referees.status}`).toBe(200)
    expect(asList(referees.body).length, 'после назначения должен быть судья').toBeGreaterThan(0)
    const leaked = emailsIn(referees.body)
    expect(leaked, `анониму видны email судей: ${leaked.join(', ')}`).toEqual([])
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'E7' }, 'второй PUT /matches/{id}/lineups не 500'), check({ id: 'E7' }), async ({ request }) => {
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const pool = await ensureLivePool(request, admin)
  const members = asList((await apiGet(request, `/teams/${pool.homeId}/members`, admin.accessToken)).body)
  let playerId = String(members[0]?.playerId || members[0]?.id || '')
  if (!playerId && !skipWithout('PLAYER')) {
    const player = await requireRole(request, 'PLAYER')
    const me = await apiGet(request, '/players/me', player.accessToken)
    playerId = String(asRecord(me.body).id || '')
    if (playerId) {
      await apiSend(request, 'POST', `/teams/${pool.homeId}/members`, {
        token: admin.accessToken,
        data: { playerId },
      })
    }
  }
  test.skip(!playerId, 'в E2E Хозяева нет игрока для состава')
  const match = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
  })
  expect(match.status, JSON.stringify(match.body)).toBe(201)
  const matchId = String(asRecord(match.body).id || '')
  const payload = { teamId: pool.homeId, starterPlayerIds: [playerId], benchPlayerIds: [] }
  try {
    const first = await apiSend(request, 'PUT', `/matches/${matchId}/lineups`, {
      token: admin.accessToken,
      data: payload,
    })
    expect(first.status, `первый PUT lineups → ${first.status} ${JSON.stringify(first.body)}`).toBeLessThan(500)
    expect(first.status, 'первый PUT должен пройти').toBe(200)
    const second = await apiSend(request, 'PUT', `/matches/${matchId}/lineups`, {
      token: admin.accessToken,
      data: payload,
    })
    expect(second.status, `второй PUT lineups → ${second.status} ${JSON.stringify(second.body)}`).not.toBe(500)
    expect(second.status, 'повторный состав не должен падать').toBe(200)
  } finally {
    await deleteMatch(request, admin, matchId)
  }
})

test(title({ id: 'E8' }, 'аватар: текст под .jpg отклоняется; анонимный /auth/photo → 401'), check({ id: 'E8' }), async ({ request }) => {
  const fan = await registerFan(request, 'photo')
  const form = new FormData()
  form.append('file', new File([TEXT_JPG], 'avatar.jpg', { type: 'image/jpeg' }))
  const spoofed = await request.post(apiPath('/auth/photo'), {
    multipart: form,
    headers: { Authorization: `Bearer ${fan.accessToken}` },
    failOnStatusCode: false,
  })
  const spoofedBody = await spoofed.text()
  expect(spoofed.status(), `текст как avatar.jpg → ${spoofed.status()} ${spoofedBody}`).toBeGreaterThanOrEqual(400)
  expect(spoofed.status(), 'не 500').toBeLessThan(500)
  expect(spoofed.status(), 'текст под .jpg должен быть отклонён, не принят').not.toBe(200)
  expect(spoofedBody, spoofedBody).toMatch(/jpeg|png|webp|gif|некоррек|нужны/i)

  const anonForm = new FormData()
  anonForm.append('file', new File([PIXEL_PNG], 'e2e-avatar.png', { type: 'image/png' }))
  const anon = await request.post(apiPath('/auth/photo'), {
    multipart: anonForm,
    failOnStatusCode: false,
  })
  expect(anon.status(), `анонимный POST /auth/photo → ${anon.status()}`).toBe(401)
})

test(title({ id: 'E9' }, 'битый JSON в /auth/login и POST /tournaments → 400'), check({ id: 'E9' }), async ({ request }) => {
  const login = await apiSend(request, 'POST', '/auth/login', {
    data: '{not json',
    headers: { 'Content-Type': 'application/json' },
  })
  expect(login.status, `битый JSON login → ${login.status} ${JSON.stringify(login.body)}`).toBe(400)
  expect(login.status).not.toBe(500)

  const reason = skipWithout('ADMIN')
  if (reason) {
    const anon = await apiSend(request, 'POST', '/tournaments', {
      data: '{',
      headers: { 'Content-Type': 'application/json' },
    })
    expect([400, 401], `битый JSON tournaments без токена → ${anon.status}`).toContain(anon.status)
    expect(anon.status).not.toBe(500)
    return
  }
  const admin = await requireRole(request, 'ADMIN')
  const created = await apiSend(request, 'POST', '/tournaments', {
    token: admin.accessToken,
    data: '{',
    headers: { 'Content-Type': 'application/json' },
  })
  expect(created.status, `битый JSON POST /tournaments → ${created.status} ${JSON.stringify(created.body)}`).toBe(400)
  expect(errorMessage(created.body) || JSON.stringify(created.body), 'нужно понятное 400, не 500').toMatch(/некоррек|json|body|тело/i)
})

test(title({ id: 'E10' }, '/auth/me у админа и судьи отдаёт не только FAN'), check({ id: 'E10' }), async ({ request }) => {
  for (const role of ['ADMIN', 'REFEREE'] as const) {
    const missing = skipWithout(role)
    if (missing) {
      test.info().annotations.push({ type: 'skip-role', description: missing })
      continue
    }
    const session = await requireRole(request, role)
    const me = await apiGet(request, '/auth/me', session.accessToken)
    expect(me.status, `GET /auth/me ${role} → ${me.status}`).toBe(200)
    const names = roleNames(me.body)
    expect(names.length, `${role}: /me без ролей`).toBeGreaterThan(0)
    expect(
      names.some((name) => name === role),
      `${role}: /me отдал только ${names.join(',')}, должна быть роль ${role}`,
    ).toBeTruthy()
    expect(names.every((name) => name === 'FAN'), `${role}: /me не должен быть только FAN (${names.join(',')})`).toBeFalsy()
  }
  test.skip(!!skipWithout('ADMIN') && !!skipWithout('REFEREE'), 'нужен E2E_ADMIN_* или E2E_REFEREE_*')
})

test(title({ id: 'E11' }, 'минута события -5 и сильно больше длительности → 400'), check({ id: 'E11' }), async ({ request }) => {
  const reason = skipWithout('ADMIN') || skipWithout('REFEREE')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const token = ctx.admin.accessToken
  try {
    const started = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token })
    expect(asRecord(started.body).status || started.status, JSON.stringify(started.body)).toBeTruthy()
    const negative = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token,
      data: { eventType: 'GOAL', teamId: ctx.homeId, gameTime: -5 },
    })
    expect(negative.status, `минута -5 → ${negative.status} ${JSON.stringify(negative.body)}`).toBe(400)
    expect(errorMessage(negative.body), errorMessage(negative.body)).toMatch(/отрицательн|некоррек/i)

    const huge = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token,
      data: { eventType: 'GOAL', teamId: ctx.homeId, gameTime: 99_999 },
    })
    expect(huge.status, `минута ≫ длительности → ${huge.status} ${JSON.stringify(huge.body)}`).toBe(400)
    expect(errorMessage(huge.body), errorMessage(huge.body)).toMatch(/длительн|больше/i)
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'E12' }, 'лимит входа: после N неверных паролей → 429, не на базовых e2e-*'), check({ id: 'E12' }), async ({ request }) => {
  test.setTimeout(90_000)
  const isolated = `203.0.113.${50 + (Date.now() % 50)}`
  const account = await registerFan(request, `ratelimit-${uniqueStamp()}`)
  expect(account.email, 'лимит гоняем на одноразовом аккаунте').toMatch(/^e2e-ratelimit-/)
  expect(account.email, 'нельзя бить по базовым E2E_*').not.toMatch(/^e2e-(fan|player|captain|referee|admin)@/i)

  let limited: { status: number; body: unknown } | undefined
  for (let i = 1; i <= 40; i++) {
    const call = await apiSend(request, 'POST', '/auth/login', {
      data: { email: account.email, password: 'DefinitelyWrongPass1' },
      headers: { 'X-Forwarded-For': isolated },
    })
    expect(call.status, `попытка ${i} не должна ронять 500`).not.toBe(500)
    if (call.status === 429) {
      limited = call
      break
    }
  }
  expect(limited, `после серии неверных паролей на ${account.email} ждали 429`).toBeTruthy()
  expect(limited?.status).toBe(429)
})
