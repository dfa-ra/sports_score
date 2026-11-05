import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

function read(path: string) {
  return readFileSync(new URL(path, import.meta.url), 'utf8')
}

function block(src: string, selector: string) {
  const start = src.indexOf(selector)
  assert.notEqual(start, -1, `missing ${selector}`)
  const open = src.indexOf('{', start)
  let depth = 0
  for (let i = open; i < src.length; i++) {
    if (src[i] === '{') depth++
    else if (src[i] === '}') {
      depth--
      if (depth === 0) return src.slice(open + 1, i)
    }
  }
  throw new Error(`unclosed ${selector}`)
}

test('team crests are fixed circles with contain, and scorers use sm', () => {
  const crest = read('../src/components/TeamCrest.vue')
  const css = crest.slice(crest.indexOf('<style'))
  assert.match(css, /border-radius:\s*50%/)
  assert.match(css, /object-fit:\s*contain/)
  assert.match(css, /image-orientation:\s*from-image/)
  assert.doesNotMatch(css, /object-fit:\s*cover/)
  assert.match(css, /\.team-crest\.sm[\s\S]*width:\s*24px/)
  assert.match(css, /\.team-crest\.md[\s\S]*width:\s*28px/)
  assert.match(css, /\.team-crest\.lg[\s\S]*width:\s*36px/)
  assert.match(css, /max-width:\s*719px[\s\S]*\.team-crest\.sm[\s\S]*width:\s*22px/)
  assert.match(css, /max-width:\s*719px[\s\S]*\.team-crest\.lg[\s\S]*width:\s*32px/)

  const home = read('../src/components/home/HomeCrest.vue')
  assert.match(home, /width:\s*28px/)
  assert.match(home, /min-width:\s*28px/)
  assert.match(home, /max-width:\s*28px/)
  assert.match(home, /object-fit:\s*contain/)
  assert.match(home, /image-orientation:\s*from-image/)
  assert.doesNotMatch(home, /object-fit:\s*cover/)

  const matches = read('../src/views/HomeView.vue')
  assert.match(matches, /\.mteam,\s*\.mc-t\s*\{[^}]*gap:\s*8px/)
  assert.match(matches, /size="sm"/)

  const scorers = read('../src/components/StatPlayerCell.vue')
  assert.match(scorers, /size="sm"/)
  assert.doesNotMatch(scorers, /:size="18"/)
})

test('player photos stay cover circles and honor image orientation', () => {
  const avatar = read('../src/components/PlayerAvatar.vue')
  const css = avatar.slice(avatar.indexOf('<style'))
  assert.match(css, /border-radius:\s*50%/)
  assert.match(css, /object-fit:\s*cover/)
  assert.match(css, /image-orientation:\s*from-image/)
  assert.match(avatar, /opacity:\s*0/)
  assert.match(avatar, /img\.ready/)
  const brand = read('../src/components/BrandMark.vue')
  assert.match(brand, /image-orientation:\s*from-image/)
})

test('header profile is only a circle and the burger stays a rounded square', () => {
  const app = read('../src/App.vue')
  const profile = block(app, '.profile-button {')
  assert.match(profile, /width:\s*40px/)
  assert.match(profile, /height:\s*40px/)
  assert.match(profile, /border-radius:\s*50%/)
  assert.match(profile, /1px solid rgba\(255,\s*255,\s*255,\s*0?\.35\)/)
  assert.doesNotMatch(profile, /border-radius:\s*12px/)
  assert.match(app, /\.profile-button:hover\s*\{\s*background:\s*rgba\(255,\s*255,\s*255,\s*0?\.14\)/)
  const burger = block(app, '.menu-button {')
  assert.match(burger, /border-radius:\s*12px/)
  assert.match(burger, /width:\s*42px/)
})

test('empty playoff card does not repeat the tab title', () => {
  const bracket = read('../src/components/PlayoffBracket.vue')
  assert.doesNotMatch(bracket, /<h2>Сетка плей-офф<\/h2>/)
  assert.doesNotMatch(bracket, /Сетка плей-офф/)
  assert.match(bracket, /<b>Сетка ещё не сформирована<\/b>/)
  assert.match(bracket, /<p>Сетка появится после групп<\/p>/)
  assert.match(bracket, /К таблице/)
  const emptyAt = bracket.indexOf('v-else-if="empty"')
  const filledHint = bracket.indexOf('Время московское')
  assert.ok(emptyAt > 0)
  assert.ok(filledHint > 0)
  assert.ok(filledHint < emptyAt)
})
