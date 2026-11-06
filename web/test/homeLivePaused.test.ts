import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { heroLivePreview, heroLiveTail } from '../src/lib/heroLive.ts'

test('a paused hero row says Пауза and does not keep the running minute', () => {
  assert.equal(heroLiveTail({ status: 'PAUSED', minute: 25 }), 'Пауза')
  assert.equal(heroLiveTail({ status: 'LIVE', minute: 18 }), '18′')
  assert.equal(heroLiveTail({ status: 'LIVE', minute: null }), 'Live')
  const home = readFileSync(new URL('../src/lib/homeOverview.ts', import.meta.url), 'utf8')
  assert.match(home, /if \(match\.status === 'PAUSED'\) return 'paused'/)
  assert.match(home, /if \(match\.status === 'PAUSED'\) return heroLiveTail\(match\)/)
  assert.match(home, /if \(match\.status === 'LIVE'\) return 'live'/)
})

test('LIVE сейчас lists a live match before a paused one and keeps the preview cap', () => {
  const paused = { id: 'paused', status: 'PAUSED' }
  const live = { id: 'live', status: 'LIVE' }
  const secondLive = { id: 'live-2', status: 'LIVE' }
  const thirdLive = { id: 'live-3', status: 'LIVE' }
  assert.deepEqual(
    heroLivePreview([paused, live]).map((match) => match.id),
    ['live', 'paused'],
  )
  assert.deepEqual(
    heroLivePreview([paused, thirdLive, secondLive, live]).map((match) => match.id),
    ['live-3', 'live-2', 'live'],
  )
})
