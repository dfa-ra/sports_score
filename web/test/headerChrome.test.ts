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

test('guest and logged-in headers share one compact cutoff', () => {
  assert.match(src, /const compactQuery = '\(max-width: 959px\)'/)
  assert.doesNotMatch(src, /device-width/)
  assert.match(src, /<div ref="menuRoot" class="menu" :class="\{ 'has-extra': extraLinks.length \}">/)
  assert.doesNotMatch(src, /v-if="[^"]*(?:isAuthenticated|extraLinks)/)
  assert.equal(src.match(/Войти/g)?.length, 1)
  assert.match(src, /class="login-pill" to="\/login">Войти<\/RouterLink>/)
  assert.match(src, /class="menu-logout only-compact"/)
  assert.match(src, /class="only-compact"/)

  const compact = mediaBlock('(max-width: 959px)')
  assert.match(compact, /\.primary \{ display: none; \}/)
  assert.match(compact, /\.menu \{ display: block; \}/)
  assert.match(compact, /\.menu-logout \{ display: flex; \}/)
  assert.doesNotMatch(compact, /\.auth/)

  const wide = mediaBlock('(min-width: 960px)')
  assert.match(wide, /\.only-compact \{ display: none; \}/)
  assert.match(wide, /\.menu\.has-extra \{ display: block; \}/)
  assert.equal([...src.matchAll(/\.menu\.has-extra/g)].length, 1)
})
