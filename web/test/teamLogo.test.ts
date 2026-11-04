import assert from 'node:assert/strict'
import test from 'node:test'
import { applyLogoToCache, logoUrlAfterUpload } from '../src/lib/teamLogo.ts'

const RED = '/media/logos/qa-logo-red.png'
const GREEN = '/media/logos/qa-logo-green.png'

test('the team store, header, and preview take the new logo from the upload response', () => {
  const seeded = applyLogoToCache({}, 'team-1', RED, '')
  assert.equal(seeded.cache['team-1'].logoUrl, RED)

  const applied = applyLogoToCache(seeded.cache, 'team-1', GREEN, RED)
  assert.equal(applied.marks.storeLogo, GREEN)
  assert.equal(applied.marks.header, GREEN)
  assert.equal(applied.marks.preview, GREEN)
  assert.equal(applied.cache['team-1'].logoUrl, GREEN)
  assert.notEqual(applied.marks.header, RED)
  assert.notEqual(applied.marks.preview, RED)
  assert.equal(seeded.cache['team-1'].logoUrl, RED)
})

test('the same logo path gets a cache-buster so the crest repaints', () => {
  const shown = logoUrlAfterUpload(RED, RED, 1_700_000_000_000)
  assert.equal(shown, `${RED}?v=1700000000000`)
  const again = logoUrlAfterUpload(shown, RED, 1_700_000_000_001)
  assert.equal(again, `${RED}?v=1700000000001`)
  const seeded = applyLogoToCache({}, 'team-1', RED, '', 1)
  const applied = applyLogoToCache(seeded.cache, 'team-1', RED, RED, 2)
  assert.equal(applied.marks.header, `${RED}?v=2`)
  assert.equal(applied.marks.preview, applied.marks.header)
  assert.equal(applied.cache['team-1'].logoUrl, applied.marks.storeLogo)
})
