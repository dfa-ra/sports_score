import assert from 'node:assert/strict'
import test from 'node:test'
import { truncateToWidth } from '../src/lib/fitLabel.ts'

test('a label that already fits is kept whole', () => {
  assert.equal(truncateToWidth('Кубок', (text) => text.length * 10, 80), 'Кубок')
})

test('a long label is cut once, with an ellipsis, and without a DOM write', () => {
  const seen: string[] = []
  const full = 'Кубок факультета программной инженерии'
  const fitted = truncateToWidth(full, (text) => {
    seen.push(text)
    return text.length * 8
  }, 48)
  assert.match(fitted, /…$/)
  assert.ok(fitted.length < full.length)
  assert.ok(seen.length < full.length)
  assert.equal(seen.includes(fitted), true)
})

test('an edge tab stays empty when even a short ellipsis cannot fit', () => {
  const fitted = truncateToWidth('Очень длинное имя', () => 400, 20, 3)
  assert.equal(fitted, '')
})
