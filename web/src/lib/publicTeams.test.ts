import assert from 'node:assert/strict'
import test from 'node:test'
import {
  TEAM_PAGE_SIZE,
  appendTeams,
  canShowMore,
  initialTeamList,
  nextTeamPage,
  readTeamPage,
  reduceTeamList,
  teamListParams,
  teamSearchQuery,
  type TeamListState,
  type TeamRow,
} from './publicTeams.ts'

function row(id: string, name: string): TeamRow {
  return { id, name, shortName: null, logoUrl: null }
}

function ready(items: TeamRow[], page: number, total: number, lastCount: number, query = ''): TeamListState {
  return {
    ...initialTeamList(),
    items,
    page,
    total,
    lastCount,
    query,
    status: items.length ? 'ready' : 'empty',
  }
}

test('query trims surrounding whitespace and collapses inner whitespace without changing case', () => {
  assert.equal(teamSearchQuery('  ЯКОРЯ   юга '), 'ЯКОРЯ юга')
  assert.equal(teamSearchQuery('\n\tкрон\u00a0\u00a0барс  '), 'крон барс')
  assert.equal(teamSearchQuery('   '), '')
  const params = teamListParams('  якоря   юга ', 0)
  assert.equal(params.q, 'якоря юга')
  assert.equal(params.page, 0)
  assert.equal(params.size, TEAM_PAGE_SIZE)
})

test('an empty query restores the unfiltered first page and never grows the page size', () => {
  const first = teamListParams('', 0)
  const later = teamListParams('   ', 4)
  assert.equal('q' in first, false)
  assert.equal('q' in later, false)
  assert.equal(later.page, 4)
  assert.equal(first.size, 20)
  assert.equal(later.size, 20)
  assert.equal(teamListParams('клуб', 3).size, TEAM_PAGE_SIZE)
  assert.notEqual(teamListParams('клуб', 3).size, 500)
})

test('a name that exists only past page 1 is addressed by q at page 0, then pages stay fixed', () => {
  const all = [
    ...Array.from({ length: 20 }, (_, index) => row(`a${index}`, `Альфа ${index}`)),
    row('yak', 'Якоря Юга'),
  ]
  const page0 = all.slice(0, TEAM_PAGE_SIZE)
  assert.equal(page0.some((item) => item.id === 'yak'), false)
  const search = teamListParams('  ЯКОРЯ   юга ', 0)
  assert.equal(search.q, 'ЯКОРЯ юга')
  assert.equal(search.page, 0)
  assert.equal(search.size, 20)
  const more = teamListParams('клуб', nextTeamPage(ready(page0, 0, 25, 20, 'клуб'), true))
  assert.deepEqual({ page: more.page, size: more.size }, { page: 1, size: 20 })
})

test('show more appends deduped rows until totalElements and then stops', () => {
  const first = [row('a', 'Альфа'), row('b', 'Бета')]
  let state = reduceTeamList(initialTeamList(), {
    type: 'success',
    query: '',
    page: 0,
    rows: first,
    total: 3,
  })
  assert.equal(canShowMore(state), true)
  state = reduceTeamList(state, {
    type: 'success',
    query: '',
    page: 1,
    rows: [row('b', 'Бета'), row('c', 'Якоря Юга')],
    total: 3,
  })
  assert.deepEqual(state.items.map((item) => item.id), ['a', 'b', 'c'])
  assert.equal(state.items.length, state.total)
  assert.equal(canShowMore(state), false)
  assert.equal(appendTeams(first, [row('b', 'Бета')]).length, 2)
})

test('changing the query resets to the first page and a stale response is ignored', () => {
  let state = ready([row('a', 'Альфа')], 2, 40, 20, 'альфа')
  state = reduceTeamList(state, { type: 'start', query: '  якоря  ', more: false })
  assert.equal(state.page, 0)
  assert.equal(state.query, 'якоря')
  assert.equal(state.status, 'loading')
  assert.deepEqual(state.items, [])
  state = reduceTeamList(state, {
    type: 'success',
    query: 'альфа',
    page: 0,
    rows: [row('a', 'Альфа Север')],
    total: 1,
  })
  assert.equal(state.status, 'loading')
  assert.deepEqual(state.items, [])
  state = reduceTeamList(state, {
    type: 'success',
    query: 'якоря',
    page: 0,
    rows: [row('yak', 'Якоря Юга')],
    total: 1,
  })
  assert.equal(state.status, 'ready')
  assert.deepEqual(state.items.map((item) => item.name), ['Якоря Юга'])
})

test('clearing the query restores the unfiltered list', () => {
  let state = ready([row('yak', 'Якоря Юга')], 0, 1, 1, 'якоря')
  state = reduceTeamList(state, { type: 'start', query: '   ', more: false })
  assert.equal(state.query, '')
  state = reduceTeamList(state, {
    type: 'success',
    query: '',
    page: 0,
    rows: [row('a', 'Альфа'), row('b', 'Бета')],
    total: 2,
  })
  assert.deepEqual(state.items.map((item) => item.id), ['a', 'b'])
  assert.equal(state.query, '')
  assert.equal(canShowMore(state), false)
})

test('an empty result stays empty and a failed request stays an error', () => {
  const empty = reduceTeamList(
    reduceTeamList(initialTeamList(), { type: 'start', query: 'нет', more: false }),
    { type: 'success', query: 'нет', page: 0, rows: [], total: 0 },
  )
  assert.equal(empty.status, 'empty')
  assert.equal(empty.query, 'нет')
  const failed = reduceTeamList(
    reduceTeamList(initialTeamList(), { type: 'start', query: 'якоря', more: false }),
    { type: 'failure', query: 'якоря', more: false },
  )
  assert.equal(failed.status, 'error')
  assert.notEqual(failed.status, 'empty')
  assert.deepEqual(failed.items, [])
  const kept = reduceTeamList(ready([row('yak', 'Якоря Юга')], 0, 5, 1, 'якоря'), {
    type: 'failure',
    query: 'якоря',
    more: true,
  })
  assert.equal(kept.status, 'ready')
  assert.equal(kept.moreError, true)
  assert.equal(kept.items.length, 1)
  assert.equal(canShowMore(kept), false)
})

test('readTeamPage uses totalElements and drops rows without an id', () => {
  const parsed = readTeamPage({
    content: [{ id: 'a', name: 'Альфа', shortName: 'А' }, { name: 'Без id' }, null],
    totalElements: 21,
    page: 0,
    size: 20,
  })
  assert.equal(parsed.total, 21)
  assert.deepEqual(parsed.rows.map((item) => item.id), ['a'])
  assert.equal(readTeamPage({ content: [] }).total, null)
})
