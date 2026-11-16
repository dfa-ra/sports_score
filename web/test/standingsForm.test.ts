import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { standingFormSlots } from '../src/lib/standings.ts'

const table = readFileSync(new URL('../src/components/StandingTable.vue', import.meta.url), 'utf8')
const groups = readFileSync(new URL('../src/components/CardsStandings.vue', import.meta.url), 'utf8')
const page = readFileSync(new URL('../src/views/TableView.vue', import.meta.url), 'utf8')
const form = readFileSync(new URL('../src/components/StandingForm.vue', import.meta.url), 'utf8')
const css = readFileSync(new URL('../src/styles/main.css', import.meta.url), 'utf8')

function letters(formMarks: Parameters<typeof standingFormSlots>[0]) {
  return standingFormSlots(formMarks).map((slot) => slot?.result ?? null)
}

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
  assert.equal(standingFormSlots([]).length, 4)
  assert.deepEqual(letters(undefined), [null, null, null, null])
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
  assert.match(mobile, /--standing-form-slot:\s*16px/)
  assert.equal(desktop.includes('--standing-form-slot: 16px'), false)
})
