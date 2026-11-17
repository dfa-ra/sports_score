import assert from 'node:assert/strict'
import test from 'node:test'
import { STANDING_FORM_LIMIT, recentForm } from './standings.ts'

test('standings form keeps the last four results, newest on the right', () => {
  assert.equal(STANDING_FORM_LIMIT, 4)
  assert.deepEqual(recentForm(['a', 'b', 'c', 'd', 'e']), ['b', 'c', 'd', 'e'])
  assert.deepEqual(recentForm(['a', 'b']), ['a', 'b'])
  assert.deepEqual(recentForm(null), [])
  assert.deepEqual(recentForm(undefined), [])
})
