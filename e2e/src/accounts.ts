import type { APIRequestContext, Page } from '@playwright/test'
import { apiGet, apiPath, apiSend, asRecord } from './api'
import {
  accounts,
  generatedPassword,
  hasRole,
  missingRoleMessage,
  type RoleAccount,
  type RoleName,
  uniqueEmail,
} from './env'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

export type Session = {
  email: string
  password: string
  accessToken: string
  refreshToken: string
  user: Record<string, unknown>
}

const PIXEL_PNG = readFileSync(join(__dirname, '..', 'fixtures', 'pixel.png'))

let cachedFan: Session | undefined

async function retrying<T>(label: string, fn: () => Promise<T>, attempts = 4): Promise<T> {
  let last: unknown
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn()
    } catch (error) {
      last = error
      const message = error instanceof Error ? error.message : String(error)
      if (!/\b(429|502|503)\b/.test(message)) throw error
      await new Promise((resolve) => setTimeout(resolve, 1500 * (i + 1)))
    }
  }
  throw last instanceof Error ? last : new Error(`${label} не удалось после повторов`)
}

export async function login(request: APIRequestContext, account: RoleAccount): Promise<Session> {
  const call = await retrying(`логин ${account.role}`, async () => {
    const response = await apiSend(request, 'POST', '/auth/login', {
      data: { email: account.email, password: account.password },
    })
    if (response.status === 429 || response.status === 502 || response.status === 503) {
      throw new Error(`Логин ${account.role} не удался: HTTP ${response.status}`)
    }
    return response
  })
  if (call.status !== 200) {
    throw new Error(`Логин ${account.role} не удался: HTTP ${call.status} ${JSON.stringify(call.body)}`)
  }
  const body = asRecord(call.body)
  return {
    email: account.email,
    password: account.password,
    accessToken: String(body.accessToken || ''),
    refreshToken: String(body.refreshToken || ''),
    user: asRecord(body.user),
  }
}

export async function registerFan(
  request: APIRequestContext,
  label = 'fan',
): Promise<Session> {
  const email = uniqueEmail(label)
  const password = generatedPassword()
  const created = await retrying('регистрация болельщика', async () => {
    const response = await apiSend(request, 'POST', '/auth/register', {
      data: {
        email,
        password,
        firstName: 'E2E',
        lastName: label,
        roles: ['FAN'],
        role: 'FAN',
      },
    })
    if (response.status === 429 || response.status === 502 || response.status === 503) {
      throw new Error(`Регистрация болельщика не удалась: HTTP ${response.status}`)
    }
    return response
  })
  if (created.status !== 201) {
    throw new Error(`Регистрация болельщика не удалась: HTTP ${created.status} ${JSON.stringify(created.body)}`)
  }
  return login(request, { role: 'FAN', email, password })
}

export async function registerPlayerRequest(
  request: APIRequestContext,
  options: { withPhoto: boolean; email?: string },
): Promise<{ email: string; password: string; status: number; body: unknown }> {
  const email = options.email || uniqueEmail('player')
  const password = generatedPassword()
  let photoUrl: string | undefined
  if (options.withPhoto) {
    const form = new FormData()
    form.append('file', new File([PIXEL_PNG], 'e2e-avatar.png', { type: 'image/png' }))
    const upload = await request.post(apiPath('/auth/photo'), {
      multipart: form,
      failOnStatusCode: false,
    })
    const uploaded = asRecord(await upload.json().catch(() => ({})))
    photoUrl = typeof uploaded.url === 'string' ? uploaded.url : undefined
  }
  const created = await apiSend(request, 'POST', '/auth/register', {
    data: {
      email,
      password,
      firstName: 'E2E',
      lastName: 'Игрок',
      roles: ['PLAYER'],
      role: 'PLAYER',
      photoUrl,
    },
  })
  return { email, password, status: created.status, body: created.body }
}

export async function fanSession(request: APIRequestContext): Promise<Session> {
  if (accounts.fan) return login(request, accounts.fan)
  if (cachedFan) return cachedFan
  cachedFan = await registerFan(request, 'session')
  return cachedFan
}

export async function requireRole(request: APIRequestContext, role: RoleName): Promise<Session> {
  const account = accounts[role.toLowerCase() as keyof typeof accounts]
  if (!account) {
    throw new Error(missingRoleMessage(role))
  }
  return login(request, account)
}

export function skipWithout(role: RoleName): string | false {
  return hasRole(role) ? false : missingRoleMessage(role)
}

export async function me(request: APIRequestContext, token: string) {
  return apiGet(request, '/auth/me', token)
}

export async function injectSession(page: Page, session: Session): Promise<void> {
  const payload = {
    access: session.accessToken,
    refresh: session.refreshToken,
    user: JSON.stringify(session.user),
  }
  await page.addInitScript(({ access, refresh, user }) => {
    localStorage.setItem('sl_access', access)
    localStorage.setItem('sl_refresh', refresh)
    localStorage.setItem('sl_user', user)
    sessionStorage.setItem('sl_access', access)
    sessionStorage.setItem('sl_refresh', refresh)
    sessionStorage.setItem('sl_user', user)
  }, payload)
}

export { PIXEL_PNG }
