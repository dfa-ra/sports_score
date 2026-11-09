import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { bracketIsEmpty } from '../src/lib/playoffGrid.ts'

const home = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
const crest = readFileSync(new URL('../src/components/home/HomeCrest.vue', import.meta.url), 'utf8')
const bracket = readFileSync(new URL('../src/components/PlayoffBracket.vue', import.meta.url), 'utf8')

test('home blocks keep their own loading, empty and error copy', () => {
  assert.match(home, /class="skeleton sk-hero"/)
  assert.match(home, /class="skeleton sk-stats"/)
  assert.match(home, /class="skeleton sk-matches"/)
  assert.match(home, /class="skeleton sk-tcard"/)
  assert.match(home, /class="skeleton sk-scorers"/)
  assert.match(home, /Не удалось загрузить матчи/)
  assert.match(home, /Матчи не загрузились/)
  assert.match(home, /Турниры не загрузились/)
  assert.match(home, /Статистика не загрузилась/)
  assert.match(home, /Матчей пока нет/)
  assert.match(home, /Нет ближайших игр/)
  assert.match(home, /Результатов пока нет/)
  assert.match(home, /Голов пока нет/)
  assert.match(home, /showStats = computed\(\(\) => !failed\.value/)
})

test('hero crests are 56 on desktop and 28 in the compact mobile band', () => {
  assert.match(crest, /\.crest\.lg \{ width: 56px; height: 56px;/)
  assert.match(crest, /@media \(max-width: 720px\) \{\s*\.crest\.lg \{ width: 28px; height: 28px;/)
  assert.doesNotMatch(crest, /width: 32px/)
  assert.match(home, /max-width: 720px\) \{[\s\S]*width: 28px; height: 28px;/)
  assert.doesNotMatch(home, /width: 32px; height: 32px;/)
})

test('playoff card keeps the approved title and the three states', () => {
  assert.match(bracket, /<h2>Сетка плей-офф<\/h2>/)
  assert.match(bracket, /aria-busy="true"/)
  assert.match(bracket, /class="sk-desk"/)
  assert.match(bracket, /class="sk-stack"/)
  assert.match(bracket, /<b>Сетка ещё не сформирована<\/b>/)
  assert.match(bracket, /Плей-офф начнётся после группового этапа\. Следите за таблицей\./)
  assert.match(bracket, /<b>Не удалось загрузить сетку<\/b>/)
  assert.match(bracket, /Проверьте соединение и попробуйте ещё раз\./)
  assert.match(bracket, />Повторить</)
  assert.match(bracket, />К таблице</)
  assert.doesNotMatch(bracket, /Сетка появится после групп/)
})

test('an unseeded bracket is empty and a waiting label is not', () => {
  assert.equal(bracketIsEmpty([]), true)
  assert.equal(bracketIsEmpty([{ slots: [{}] }]), true)
  assert.equal(bracketIsEmpty([{ slots: [{ homeWaitingLabel: 'Победитель 1/4 №1' }] }]), false)
  assert.equal(bracketIsEmpty([{ slots: [{ homeTeamId: 'a' }] }]), false)
})
