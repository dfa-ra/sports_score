import assert from 'node:assert/strict'
import test from 'node:test'
import { registeredName } from './playerTitle.ts'

test('roster and lineup titles are the registration name, not the shirt nickname', () => {
  const roster = {
    playerLastName: 'Иванов',
    playerFirstName: 'Иван',
    displayName: 'Vanya',
  }
  const lineup = {
    lastName: 'Иванов',
    firstName: 'Иван',
    name: 'Vanya',
  }
  const protocol = {
    playerLastName: 'Иванов',
    playerFirstName: 'Иван',
    playerName: 'Vanya',
    secondaryPlayerLastName: 'Петров',
    secondaryPlayerFirstName: 'Пётр',
    secondaryPlayerName: 'Petya',
  }

  assert.equal(registeredName(roster), 'Иванов Иван')
  assert.equal(registeredName(lineup), 'Иванов Иван')
  assert.equal(registeredName(protocol), 'Иванов Иван')
  assert.equal(registeredName(protocol, 'secondary'), 'Петров Пётр')
  assert.notEqual(registeredName(roster), 'Vanya')
  assert.notEqual(registeredName(lineup), 'Vanya')
})
