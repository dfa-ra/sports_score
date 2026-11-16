import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { standingFormFromNewestFirst, standingFormSlots } from '../src/lib/standings.ts'

const table = readFileSync(new URL('../src/components/StandingTable.vue', import.meta.url), 'utf8')
const groups = readFileSync(new URL('../src/components/CardsStandings.vue', import.meta.url), 'utf8')
const page = readFileSync(new URL('../src/views/TableView.vue', import.meta.url), 'utf8')
const form = readFileSync(new URL('../src/components/StandingForm.vue', import.meta.url), 'utf8')
const css = readFileSync(new URL('../src/styles/main.css', import.meta.url), 'utf8')
const matchPage = readFileSync(new URL('../src/views/MatchDetailView.vue', import.meta.url), 'utf8')
const tournamentPage = readFileSync(new URL('../src/views/TournamentDetailView.vue', import.meta.url), 'utf8')

function letters(formMarks: Parameters<typeof standingFormSlots>[0]) {
  return standingFormSlots(formMarks).map((slot) => slot?.result ?? null)
}

test('zero played matches are four empty slots', () => {
  assert.equal(standingFormSlots([]).length, 4)
  assert.deepEqual(letters([]), [null, null, null, null])
  assert.deepEqual(letters(undefined), [null, null, null, null])
  assert.deepEqual(letters(null), [null, null, null, null])
})

test('one played match sits in the rightmost slot', () => {
  const slots = standingFormSlots([{ result: 'L', technical: true }])
  assert.deepEqual(letters([{ result: 'W', technical: false }]), [null, null, null, 'W'])
  assert.equal(slots[3]?.result, 'L')
  assert.equal(slots[3]?.technical, true)
  assert.equal(slots[0], null)
  assert.equal(slots[2], null)
})

test('two played matches keep empty slots on the left and the newest on the right', () => {
  const slots = standingFormSlots([
    { result: 'W', technical: false },
    { result: 'L', technical: true },
  ])
  assert.deepEqual(letters([
    { result: 'W', technical: false },
    { result: 'L', technical: true },
  ]), [null, null, 'W', 'L'])
  assert.equal(slots[2]?.technical, false)
  assert.equal(slots[3]?.technical, true)
  assert.equal(slots[0], null)
})

test('three played matches pack to the right with the newest last', () => {
  const slots = standingFormSlots([
    { result: 'D', technical: false },
    { result: 'W', technical: false },
    { result: 'L', technical: true },
  ])
  assert.deepEqual(letters([
    { result: 'D', technical: false },
    { result: 'W', technical: false },
    { result: 'L', technical: true },
  ]), [null, 'D', 'W', 'L'])
  assert.equal(slots[3]?.technical, true)
  assert.equal(slots[0], null)
})

test('more than four form marks keep the last four without changing their order', () => {
  const five = [
    { result: 'D', technical: false },
    { result: 'W', technical: false },
    { result: 'L', technical: true },
    { result: 'W', technical: false },
    { result: 'L', technical: true },
  ]
  const copy = JSON.parse(JSON.stringify(five))
  assert.deepEqual(letters(five), ['W', 'L', 'W', 'L'])
  assert.deepEqual(standingFormSlots(five).map((slot) => slot?.technical ?? null), [false, true, false, true])
  assert.deepEqual(five, copy)

  const seven = ['W', 'D', 'L', 'W', 'W', 'D', 'L']
  assert.deepEqual(letters(seven), ['W', 'W', 'D', 'L'])
})

test('a mutual forfeit counts as played and stays newest on the right', () => {
  const slots = standingFormSlots([
    { result: 'W', technical: false },
    { result: 'D', technical: false },
    { result: 'W', technical: false },
    { result: 'L', technical: true },
    { result: 'L', technical: true },
  ])
  assert.deepEqual(slots.map((slot) => slot?.result), ['D', 'W', 'L', 'L'])
  assert.deepEqual(slots.map((slot) => slot?.technical), [false, false, true, true])
})

test('a newest-first list is shown oldest on the left and newest on the right', () => {
  assert.deepEqual(
    standingFormFromNewestFirst(['В', 'Н', 'П']).map((slot) => slot?.result ?? null),
    [null, 'П', 'Н', 'В'],
  )
  assert.deepEqual(
    standingFormFromNewestFirst([
      { letter: 'П', technical: true },
      { letter: 'В', technical: false },
    ]).map((slot) => ({ result: slot?.result ?? null, technical: slot?.technical ?? null })),
    [
      { result: null, technical: null },
      { result: null, technical: null },
      { result: 'В', technical: false },
      { result: 'П', technical: true },
    ],
  )
})

test('an empty slot is not a technical forfeit and string marks stay results', () => {
  const slots = standingFormSlots(['В', 'П'])
  assert.equal(slots[0], null)
  assert.equal(slots[1], null)
  assert.deepEqual(slots[2], { result: 'В', technical: false })
  assert.deepEqual(slots[3], { result: 'П', technical: false })
})

test('standings tables use a fixed form column instead of a content-sized one', () => {
  for (const source of [table, groups, page]) {
    assert.equal(source.includes('minmax(5.6rem, auto)'), false)
    assert.match(source, /var\(--standing-form-col\)/)
  }
  assert.match(table, /StandingForm/)
  assert.match(groups, /StandingForm/)
  assert.match(form, /standingFormSlots/)
  assert.match(form, /flex-wrap:\s*nowrap/)
  assert.match(form, /class="pip blank"|'blank'/)
  assert.match(form, />ТП</)
  const desktop = css.slice(0, css.indexOf('@media (max-width: 720px)'))
  const mobile = css.slice(css.indexOf('@media (max-width: 720px)'))
  assert.match(desktop, /--standing-form-slot:\s*18px/)
  assert.match(desktop, /--standing-form-gap:\s*4px/)
  assert.match(mobile, /--standing-form-slot:\s*16px/)
  assert.match(mobile, /--standing-form-gap:\s*4px/)
  assert.equal(desktop.includes('--standing-form-slot: 16px'), false)
  assert.equal(css.includes('--standing-form-gap: 3px'), false)
  assert.match(form, /1\.5px solid #c5d0dc/)
  assert.match(form, /pip\.pending/)
  assert.match(table, /subgrid/)
  assert.match(groups, /subgrid/)
  assert.match(page, /subgrid/)
  assert.match(table, /var\(--standing-goals\)/)
  assert.match(groups, /var\(--standing-goals\)/)
  assert.match(desktop, /--standing-goals:\s*4\.75rem/)
  assert.match(desktop, /--standing-o-form-gap:\s*12px/)
  assert.match(desktop, /--standing-points:\s*2\.75rem/)
  assert.match(desktop, /--standing-form-track:/)
  assert.match(mobile, /--standing-goals:\s*52px/)
  assert.match(table, /var\(--standing-points\)/)
  assert.match(table, /var\(--standing-form-track\)/)
  assert.match(groups, /var\(--standing-points\)/)
  assert.match(groups, /var\(--standing-form-track\)/)
  assert.match(table, /padding-left:\s*16px/)
  assert.match(groups, /column-gap:\s*6px/)
  assert.match(table, /justify-content:\s*flex-end/)
  assert.match(groups, /justify-content:\s*flex-end/)
  assert.match(page, /StandingForm pending/)
  assert.match(matchPage, /StandingForm/)
  assert.match(matchPage, /standingFormFromNewestFirst/)
  assert.match(matchPage, /последние 4/)
  assert.match(tournamentPage, /StandingForm pending/)
  assert.equal(matchPage.includes('нет матчей'), false)
})
