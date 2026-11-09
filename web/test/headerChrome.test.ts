import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const src = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

function mediaBlock(query: string) {
  const marker = `@media ${query} {`
  const start = src.indexOf(marker)
  assert.notEqual(start, -1, `missing ${marker}`)
  let depth = 0
  for (let i = src.indexOf('{', start); i < src.length; i++) {
    if (src[i] === '{') depth++
    else if (src[i] === '}') {
      depth--
      if (depth === 0) return src.slice(src.indexOf('{', start) + 1, i)
    }
  }
  throw new Error(`unclosed ${marker}`)
}

test('wide screens have a profile menu and no burger; narrow screens have only the burger', () => {
  assert.match(src, /const compactQuery = '\(max-width: 1023px\)'/)
  assert.doesNotMatch(src, /device-width/)
  assert.match(src, /<div ref="menuRoot" class="menu">/)
  assert.doesNotMatch(src, /has-extra/)
  assert.doesNotMatch(src, /only-compact/)
  assert.equal(src.match(/Войти/g)?.length, 1)
  assert.match(src, /class="login-pill" to="\/login">Войти<\/RouterLink>/)
  assert.match(src, /class="auth-pending"/)
  assert.match(src, /class="profile-button"/)
  assert.match(src, /aria-label="Профиль"/)
  assert.match(src, /class="menu-sep" role="separator"/)
  assert.match(src, /headerAccountLinks/)

  const compact = mediaBlock('(max-width: 1023px)')
  assert.match(compact, /\.primary \{ display: none; \}/)
  assert.match(compact, /\.menu \{ display: block; \}/)
  assert.match(compact, /\.account \{ display: block; \}/)
  assert.doesNotMatch(compact, /^\s*\.auth \{/m)

  const wide = mediaBlock('(min-width: 1024px)')
  assert.match(wide, /\.menu \{ display: none; \}/)
  assert.match(wide, /\.account \{ display: block; \}/)
  assert.doesNotMatch(wide, /has-extra/)
})
