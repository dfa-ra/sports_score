import assert from 'node:assert/strict'
import test from 'node:test'
import { initials } from '../src/lib/format.ts'
import { avatarPhotoCovers, personInitials } from '../src/lib/personAvatar.ts'

test('initials come from the first letters of the name', () => {
  assert.equal(initials('Максим Волков'), 'МВ')
  assert.equal(initials('  кирилл   лебедев '), 'КЛ')
  assert.equal(initials('Белов'), 'Б')
  assert.equal(initials(''), 'SL')
  assert.equal(initials(null), 'SL')
})

test('person avatar letters follow the registered name: surname, then given name', () => {
  assert.equal(personInitials('Максим', 'Волков'), 'ВМ')
  assert.equal(personInitials(' максим ', ' волков '), 'ВМ')
  assert.equal(personInitials('Максим', null), 'М')
  assert.equal(personInitials(null, 'Волков'), 'В')
  assert.equal(personInitials('', '  '), 'SL')
  assert.equal(personInitials(null, null), 'SL')
})

test('a photo covers initials only after it has loaded', () => {
  assert.equal(avatarPhotoCovers(null, false, false), false)
  assert.equal(avatarPhotoCovers('   ', true, false), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', false, false), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', false, true), false)
  assert.equal(avatarPhotoCovers('/media/missing.jpg', true, true), false)
  assert.equal(avatarPhotoCovers('/media/photo.jpg', true, false), true)
})
