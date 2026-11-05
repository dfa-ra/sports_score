import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const crest = readFileSync(new URL('../src/components/TeamCrest.vue', import.meta.url), 'utf8')
const home = readFileSync(new URL('../src/components/home/HomeCrest.vue', import.meta.url), 'utf8')
const avatar = readFileSync(new URL('../src/components/PlayerAvatar.vue', import.meta.url), 'utf8')
const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const bracket = readFileSync(new URL('../src/components/PlayoffBracket.vue', import.meta.url), 'utf8')

function block(source: string, start: string, end: string) {
  const from = source.indexOf(start)
  assert.notEqual(from, -1, start)
  const to = source.indexOf(end, from + start.length)
  assert.notEqual(to, -1, end)
  return source.slice(from, to)
}

test('team crests and player photos are fixed circles with the whole picture centered', () => {
  assert.match(crest, /border-radius:\s*50%/)
  assert.match(crest, /object-fit:\s*contain/)
  assert.match(crest, /object-position:\s*center/)
  assert.match(crest, /image-orientation:\s*from-image/)
  assert.doesNotMatch(crest, /object-fit:\s*cover/)
  assert.doesNotMatch(crest, /padding:/)
  assert.match(home, /TeamCrest/)
  assert.doesNotMatch(home, /object-fit:\s*cover/)
  assert.match(avatar, /object-fit:\s*contain/)
  assert.match(avatar, /image-orientation:\s*from-image/)
  assert.doesNotMatch(avatar, /object-fit:\s*cover/)
})

test('the header profile control is only a circle', () => {
  const button = block(app, '.profile-button {', '.profile-button:hover')
  assert.match(button, /border-radius:\s*50%/)
  assert.doesNotMatch(button, /12px/)
  assert.doesNotMatch(button, /border-radius:\s*12px/)
})

test('the empty playoff card does not repeat the tab name', () => {
  const empty = block(bracket, 'v-else-if="empty"', 'v-else-if="editable"')
  assert.match(empty, /Сетка ещё не сформирована/)
  assert.match(empty, /Сетка появится после групп/)
  assert.match(empty, /К таблице/)
  assert.doesNotMatch(empty, /<h2/)
  assert.doesNotMatch(bracket, /Сетка плей-офф/)
})
