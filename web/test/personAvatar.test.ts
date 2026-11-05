import assert from 'node:assert/strict'
import test from 'node:test'
import { initials } from '../src/lib/format.ts'
import { avatarPhotoCovers } from '../src/lib/personAvatar.ts'

test('initials come from the first letters of the name', () => {
  assert.equal(initials('Максим Волков'), 'МВ')
  assert.equal(initials('  кирилл   лебедев '), 'КЛ')
  assert.equal(initials('Белов'), 'Б')
  assert.equal(initials(''), 'SL')
  assert.equal(initials(null), 'SL')
})

test('a photo covers initials only after it has loaded', () => {
  assert.equal(avatarPhotoCovers(null, false, false), false)
  assert.equal(avatarPhotoCovers('   ', true, false), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', false, false), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', false, true), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', true, true), false)
  assert.equal(avatarPhotoCovers('/media/photo.jpg', true, false), true)
})
