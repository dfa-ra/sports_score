import assert from 'node:assert/strict'
import test from 'node:test'
import { headerAccountLinks, headerAuthSlot, headerExtraLinks, type HeaderNavRole } from './headerAuth.ts'

function roles(...list: HeaderNavRole[]) {
  return (role: HeaderNavRole) => list.includes(role)
}

function labels(list: HeaderNavRole[]) {
  return headerExtraLinks(roles(...list)).map((item) => item.label)
}

test('guest burger has no role items', () => {
  assert.deepEqual(headerExtraLinks(roles()), [])
})

test('admin sees Админка only, not Пульт судьи, Аналитик, or Моя команда', () => {
  assert.deepEqual(labels(['ADMIN']), ['Админка'])
})

test('referee sees Пульт судьи only', () => {
  assert.deepEqual(labels(['REFEREE']), ['Пульт судьи'])
})

test('analyst is a separate role and is not granted by admin', () => {
  assert.deepEqual(labels(['ANALYST']), ['Аналитик'])
  assert.deepEqual(labels(['ADMIN', 'ANALYST']), ['Админка', 'Аналитик'])
})

test('my team is player or captain, including when they also have a staff role', () => {
  assert.deepEqual(labels(['PLAYER']), ['Моя команда'])
  assert.deepEqual(labels(['CAPTAIN']), ['Моя команда'])
  assert.deepEqual(labels(['PLAYER', 'REFEREE']), ['Моя команда', 'Пульт судьи'])
  assert.deepEqual(labels(['CAPTAIN', 'ADMIN']), ['Моя команда', 'Админка'])
  assert.deepEqual(labels(['PLAYER', 'ADMIN', 'REFEREE', 'ANALYST']), [
    'Моя команда',
    'Админка',
    'Пульт судьи',
    'Аналитик',
  ])
})

test('the account menu starts with Профиль and then the same role rows', () => {
  assert.deepEqual(headerAccountLinks(roles()).map((item) => item.label), ['Профиль'])
  assert.deepEqual(headerAccountLinks(roles('REFEREE')).map((item) => item.label), ['Профиль', 'Пульт судьи'])
  assert.deepEqual(headerAccountLinks(roles('ADMIN')).map((item) => item.label), ['Профиль', 'Админка'])
  assert.deepEqual(headerAccountLinks(roles('ANALYST')).map((item) => item.label), ['Профиль', 'Аналитик'])
  assert.deepEqual(headerAccountLinks(roles('PLAYER')).map((item) => item.label), ['Профиль', 'Моя команда'])
})

test('header shows neither login nor avatar until the session check finishes', () => {
  assert.equal(headerAuthSlot(false, false), 'pending')
  assert.equal(headerAuthSlot(false, true), 'pending')
  assert.equal(headerAuthSlot(true, true), 'account')
  assert.equal(headerAuthSlot(true, false), 'login')
})
