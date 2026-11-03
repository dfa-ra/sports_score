import assert from 'node:assert/strict'
import test from 'node:test'
import { headerAuthSlot, headerExtraLinks, type HeaderNavRole } from './headerAuth.ts'

function roles(...list: HeaderNavRole[]) {
  return (role: HeaderNavRole) => list.includes(role)
}

function labels(list: HeaderNavRole[]) {
  return headerExtraLinks(roles(...list)).map((item) => item.label)
}

test('guest burger has no role items', () => {
  assert.deepEqual(headerExtraLinks(roles()), [])
})

test('admin sees Админ only, not Судья, Аналитик, or Моя команда', () => {
  assert.deepEqual(labels(['ADMIN']), ['Админ'])
})

test('referee sees Судья only', () => {
  assert.deepEqual(labels(['REFEREE']), ['Судья'])
})

test('analyst is a separate role and is not granted by admin', () => {
  assert.deepEqual(labels(['ANALYST']), ['Аналитик'])
  assert.deepEqual(labels(['ADMIN', 'ANALYST']), ['Админ', 'Аналитик'])
})

test('my team is player or captain, including when they also have a staff role', () => {
  assert.deepEqual(labels(['PLAYER']), ['Моя команда'])
  assert.deepEqual(labels(['CAPTAIN']), ['Моя команда'])
  assert.deepEqual(labels(['PLAYER', 'REFEREE']), ['Моя команда', 'Судья'])
  assert.deepEqual(labels(['CAPTAIN', 'ADMIN']), ['Моя команда', 'Админ'])
  assert.deepEqual(labels(['PLAYER', 'ADMIN', 'REFEREE', 'ANALYST']), [
    'Моя команда',
    'Админ',
    'Судья',
    'Аналитик',
  ])
})

test('header shows neither login nor avatar until the session check finishes', () => {
  assert.equal(headerAuthSlot(false, false), 'pending')
  assert.equal(headerAuthSlot(false, true), 'pending')
  assert.equal(headerAuthSlot(true, true), 'account')
  assert.equal(headerAuthSlot(true, false), 'login')
})
