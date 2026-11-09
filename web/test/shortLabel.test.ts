import assert from 'node:assert/strict'
import test from 'node:test'
import { distinctLabels } from '../src/lib/shortLabel.ts'

test('a shared prefix drops and the distinctive tail stays', () => {
  assert.deepEqual(
    distinctLabels(['QA Толян Кубок 2', 'QA Толян Кубок 3']),
    ['Кубок 2', 'Кубок 3'],
  )
})

test('names that do not share a start stay whole', () => {
  assert.deepEqual(distinctLabels(['Лига ИТМО', 'Осенний кубок']), ['Лига ИТМО', 'Осенний кубок'])
})

test('one name is unchanged', () => {
  assert.deepEqual(distinctLabels(['QA Толян Кубок 2']), ['QA Толян Кубок 2'])
})
