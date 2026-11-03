import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { runningCardMatches } from '../src/lib/runningCard.ts'

const past = '2020-01-01T00:00:00Z'
const future = '2099-05-01T12:00:00Z'

function row(id: string, status: string, scheduledAt: string | null, tournamentId = 'tolyan') {
  return { id, tournamentId, status, scheduledAt }
}

test('a live match with a null or past kickoff still appears on the card', () => {
  const undated = row('live-null', 'LIVE', null)
  const stale = row('live-past', 'LIVE', past)
  const paused = row('paused-past', 'PAUSED', past)
  const emptyCard = { id: 'tolyan', lastMatch: null, nextMatch: null }

  assert.deepEqual(runningCardMatches(emptyCard, [undated]).map((match) => match.id), ['live-null'])
  assert.deepEqual(runningCardMatches(emptyCard, [stale]).map((match) => match.id), ['live-past'])
  assert.deepEqual(runningCardMatches(emptyCard, [paused]).map((match) => match.id), ['paused-past'])
})

test('an in-play match wins over a future kickoff, and another tournament stays out', () => {
  const live = row('live-past', 'LIVE', past)
  const other = row('other-live', 'LIVE', null, 'other')
  const upcoming = row('soon', 'SCHEDULED', future)
  const picked = runningCardMatches(
    { id: 'tolyan', lastMatch: null, nextMatch: upcoming },
    [other, live],
  )
  assert.deepEqual(picked.map((match) => match.id), ['live-past'])
})

test('without an in-play match the card keeps the last result and the nearest upcoming one', () => {
  const last = row('done', 'FINISHED', past)
  const next = row('soon', 'SCHEDULED', future)
  assert.deepEqual(
    runningCardMatches({ id: 'tolyan', lastMatch: last, nextMatch: next }, []).map((match) => match.id),
    ['done', 'soon'],
  )
  assert.deepEqual(
    runningCardMatches({ id: 'tolyan', lastMatch: null, nextMatch: next }, []).map((match) => match.id),
    ['soon'],
  )
  assert.deepEqual(
    runningCardMatches({ id: 'tolyan', lastMatch: last, nextMatch: null }, [row('scheduled-past', 'SCHEDULED', past)]).map((match) => match.id),
    ['done'],
  )
})

test('the empty line is only when the tournament has no in-play, last, or upcoming match', () => {
  assert.deepEqual(runningCardMatches({ id: 'tolyan', lastMatch: null, nextMatch: null }, []), [])
  assert.deepEqual(
    runningCardMatches({ id: 'tolyan', lastMatch: null, nextMatch: null }, [row('elsewhere', 'LIVE', null, 'other')]),
    [],
  )
})

test('the home card reads the same live rows the hero and the socket update', () => {
  const src = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
  assert.match(src, /runningCardMatches\(tournament, liveRows\.value\)/)
  assert.match(src, /live: overview\.value\.live\.map\(patch\)\.filter\(\(match\) => match\.status === 'LIVE' \|\| match\.status === 'PAUSED'\)/)
})
