import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ACCESS_STORAGE_KEY,
  REFRESH_COOKIE_NAME,
  REFRESH_MAX_AGE_SECONDS,
  REFRESH_STORAGE_KEY,
  accessTokenStillValid,
  loadRefreshToken,
  readCookieValue,
  refreshCookieAssignment,
  writeSessionValues,
  type KeyValueStore,
} from './refreshSession.ts'

class MemoryStore implements KeyValueStore {
  readonly items = new Map<string, string>()
  getItem(key: string) {
    return this.items.has(key) ? this.items.get(key)! : null
  }
  setItem(key: string, value: string) {
    this.items.set(key, value)
  }
  removeItem(key: string) {
    this.items.delete(key)
  }
}

function jwt(exp: number) {
  const payload = Buffer.from(JSON.stringify({ exp }), 'utf8').toString('base64url')
  return `eyJhbGciOiJIUzI1NiJ9.${payload}.sig`
}

test('refresh cookie lives 7 days, Lax, path /, and Secure only on https', () => {
  assert.equal(REFRESH_MAX_AGE_SECONDS, 604800)
  const https = refreshCookieAssignment('refresh-token-value', true)
  assert.match(https, new RegExp(`^${REFRESH_COOKIE_NAME}=refresh-token-value;`))
  assert.match(https, /Path=\//)
  assert.match(https, /Max-Age=604800/)
  assert.match(https, /SameSite=Lax/)
  assert.match(https, /Secure/)
  assert.equal(https.includes('sl_access'), false)

  const http = refreshCookieAssignment('refresh-token-value', false)
  assert.equal(http.includes('Secure'), false)
  assert.match(http, /Max-Age=604800/)
})

test('clearing the session expires the cookie and does not keep the token', () => {
  const cleared = refreshCookieAssignment(null, true)
  assert.match(cleared, new RegExp(`^${REFRESH_COOKIE_NAME}=;`))
  assert.match(cleared, /Max-Age=0/)
  assert.match(cleared, /Path=\//)
  assert.match(cleared, /SameSite=Lax/)
  assert.match(cleared, /Secure/)
  assert.equal(cleared.includes('refresh-token-value'), false)
})

test('startup prefers localStorage and otherwise recovers the refresh token from the cookie', () => {
  const local = new MemoryStore()
  local.setItem(REFRESH_STORAGE_KEY, 'from-local')
  assert.equal(
    loadRefreshToken(local, `${REFRESH_COOKIE_NAME}=from-cookie`, null),
    'from-local',
  )

  const empty = new MemoryStore()
  const recovered = loadRefreshToken(empty, `theme=dark; ${REFRESH_COOKIE_NAME}=from-cookie; other=1`, null)
  assert.equal(recovered, 'from-cookie')
  assert.equal(empty.getItem(REFRESH_STORAGE_KEY), 'from-cookie')
})

test('a previous tab session is promoted when localStorage and the cookie are empty', () => {
  const local = new MemoryStore()
  const session = new MemoryStore()
  session.setItem(REFRESH_STORAGE_KEY, 'from-tab')
  assert.equal(loadRefreshToken(local, '', session), 'from-tab')
  assert.equal(local.getItem(REFRESH_STORAGE_KEY), 'from-tab')
})

test('logout removes access, refresh, and the user card from both stores', () => {
  const local = new MemoryStore()
  const session = new MemoryStore()
  writeSessionValues(local, session, {
    access: 'access-jwt',
    refresh: 'refresh-token',
    user: '{"id":"1"}',
  })
  writeSessionValues(local, session, { access: null, refresh: null, user: null })
  assert.equal(local.getItem(ACCESS_STORAGE_KEY), null)
  assert.equal(local.getItem(REFRESH_STORAGE_KEY), null)
  assert.equal(session.getItem('sl_user'), null)
})

test('an expired access token is not treated as a live session', () => {
  const now = 1_700_000_000_000
  assert.equal(accessTokenStillValid(jwt(Math.floor(now / 1000) + 120), now), true)
  assert.equal(accessTokenStillValid(jwt(Math.floor(now / 1000) - 5), now), false)
  assert.equal(accessTokenStillValid(jwt(Math.floor(now / 1000) + 5), now), false)
  assert.equal(accessTokenStillValid('not-a-jwt', now), false)
})

test('cookie reader ignores a similarly named cookie', () => {
  assert.equal(readCookieValue('sl_refresh_extra=no; sl_refresh=yes', REFRESH_COOKIE_NAME), 'yes')
  assert.equal(readCookieValue('', REFRESH_COOKIE_NAME), null)
})
