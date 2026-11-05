import assert from 'node:assert/strict'
import test from 'node:test'
import { footerVersionLabel, footerVersionTitle } from '../src/lib/appVersion.ts'

test('prod footer is the version only', () => {
  assert.equal(footerVersionLabel('0.2.62', 'abcdef1', 'prod'), 'v0.2.62')
  assert.equal(footerVersionTitle('0.2.62', 'abcdef1', 'prod'), 'v0.2.62')
  assert.equal(footerVersionLabel('0.2.62', 'abcdef1', 'prod').includes('abcdef1'), false)
})

test('dev footer keeps the version and the commit hash', () => {
  assert.equal(footerVersionLabel('0.2.62', 'abcdef1', 'dev'), 'v0.2.62 · abcdef1')
  assert.equal(footerVersionTitle('0.2.62', 'abcdef1', 'dev'), 'dev · v0.2.62 · abcdef1')
})

test('a local build without a revision stays on the version', () => {
  assert.equal(footerVersionLabel('0.2.62', 'local', 'local'), 'v0.2.62')
  assert.equal(footerVersionLabel('0.2.62', '', 'dev'), 'v0.2.62')
})
