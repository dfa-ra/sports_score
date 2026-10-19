/**
 * Refresh-token persistence for the installed site.
 *
 * The access JWT stays out of cookies: it is larger and short-lived.
 * The opaque refresh token is enough to restore the session.
 *
 * Max-Age matches `app.jwt.refresh-expiration-ms` in application.yml
 * (604800000 ms = 7 days). Do not log the token.
 */
export const REFRESH_MAX_AGE_SECONDS = 7 * 24 * 60 * 60

export const ACCESS_STORAGE_KEY = 'sl_access'
export const REFRESH_STORAGE_KEY = 'sl_refresh'
export const USER_STORAGE_KEY = 'sl_user'
export const REFRESH_COOKIE_NAME = 'sl_refresh'

export interface KeyValueStore {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export function refreshCookieAssignment(token: string | null, secure: boolean): string {
  const parts = [
    `${REFRESH_COOKIE_NAME}=${token ? encodeURIComponent(token) : ''}`,
    'Path=/',
    `Max-Age=${token ? REFRESH_MAX_AGE_SECONDS : 0}`,
    'SameSite=Lax',
  ]
  if (secure) parts.push('Secure')
  return parts.join('; ')
}

export function readCookieValue(cookieHeader: string, name: string): string | null {
  for (const part of cookieHeader.split(';')) {
    const trimmed = part.trim()
    const eq = trimmed.indexOf('=')
    if (eq <= 0) continue
    if (trimmed.slice(0, eq) !== name) continue
    const raw = trimmed.slice(eq + 1)
    if (!raw) return null
    try {
      return decodeURIComponent(raw)
    } catch {
      return raw
    }
  }
  return null
}

function readStore(store: KeyValueStore | null, key: string): string | null {
  if (!store) return null
  try {
    const value = store.getItem(key)
    return value && value.length > 0 ? value : null
  } catch {
    return null
  }
}

function writeStore(store: KeyValueStore | null, key: string, value: string | null) {
  if (!store) return
  try {
    if (value) store.setItem(key, value)
    else store.removeItem(key)
  } catch {
    // Private mode and some embedded webviews reject storage. The cookie copy still counts.
  }
}

export function loadRefreshToken(
  local: KeyValueStore | null,
  cookieHeader: string,
  session: KeyValueStore | null = null,
): string | null {
  const fromLocal = readStore(local, REFRESH_STORAGE_KEY)
  if (fromLocal) return fromLocal
  const fromCookie = readCookieValue(cookieHeader, REFRESH_COOKIE_NAME)
  if (fromCookie) {
    writeStore(local, REFRESH_STORAGE_KEY, fromCookie)
    return fromCookie
  }
  const fromSession = readStore(session, REFRESH_STORAGE_KEY)
  if (fromSession) {
    writeStore(local, REFRESH_STORAGE_KEY, fromSession)
    return fromSession
  }
  return null
}

export function loadStoredValue(
  key: string,
  local: KeyValueStore | null,
  session: KeyValueStore | null = null,
): string | null {
  return readStore(local, key) || readStore(session, key)
}

export function writeSessionValues(
  local: KeyValueStore | null,
  session: KeyValueStore | null,
  values: { access: string | null; refresh: string | null; user: string | null },
) {
  for (const store of [local, session]) {
    writeStore(store, ACCESS_STORAGE_KEY, values.access)
    writeStore(store, REFRESH_STORAGE_KEY, values.refresh)
    writeStore(store, USER_STORAGE_KEY, values.user)
  }
}

/** True when the access JWT exp is still far enough ahead to use without a refresh. */
export function accessTokenStillValid(token: string, nowMs = Date.now(), skewMs = 15_000): boolean {
  const payload = token.split('.')[1]
  if (!payload) return false
  try {
    const json = JSON.parse(decodeBase64Url(payload)) as { exp?: unknown }
    return typeof json.exp === 'number' && json.exp * 1000 > nowMs + skewMs
  } catch {
    return false
  }
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const pad = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  const binary = globalThis.atob(normalized + pad)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}
