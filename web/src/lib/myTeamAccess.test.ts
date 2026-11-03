import assert from 'node:assert/strict'
import test from 'node:test'
import { myTeamStub, showsMyTeamRoster, type RosterRole } from './myTeamAccess.ts'

function has(roles: readonly string[]) {
  return (role: RosterRole) => roles.includes(role)
}

test('a player or captain keeps the roster page, including when they also hold a staff role', () => {
  assert.equal(showsMyTeamRoster(has(['PLAYER'])), true)
  assert.equal(showsMyTeamRoster(has(['CAPTAIN'])), true)
  assert.equal(showsMyTeamRoster(has(['ADMIN', 'PLAYER'])), true)
  assert.equal(showsMyTeamRoster(has(['ANALYST', 'CAPTAIN'])), true)
})

test('admin, referee, and analyst without a roster role see the player stub', () => {
  assert.equal(showsMyTeamRoster(has([])), false)
  assert.equal(showsMyTeamRoster(has(['ADMIN'])), false)
  assert.equal(showsMyTeamRoster(has(['REFEREE'])), false)
  assert.equal(showsMyTeamRoster(has(['ANALYST'])), false)
  assert.equal(showsMyTeamRoster(has(['FAN'])), false)
  assert.equal(myTeamStub.title, 'Страница для игроков и капитанов')
  assert.equal(myTeamStub.homeTo, '/')
  assert.equal(myTeamStub.homeLabel, 'Главная')
})
