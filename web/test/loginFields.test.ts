import assert from 'node:assert/strict'
import test from 'node:test'
import {
  LOGIN_EMAIL_FORMAT,
  LOGIN_EMAIL_REQUIRED,
  LOGIN_NETWORK,
  LOGIN_PAIR_INVALID,
  LOGIN_PASSWORD_REQUIRED,
  loginFieldErrors,
  loginSubmitFailure,
} from '../src/lib/loginFields.ts'

test('an empty login asks for email and password', () => {
  assert.deepEqual(loginFieldErrors('', ''), {
    email: LOGIN_EMAIL_REQUIRED,
    password: LOGIN_PASSWORD_REQUIRED,
  })
  assert.deepEqual(loginFieldErrors('   ', ''), {
    email: 'Введите email',
    password: 'Введите пароль',
  })
})

test('e-mail «abc» is a format error and does not replace a filled password', () => {
  assert.deepEqual(loginFieldErrors('abc', 'secret'), {
    email: LOGIN_EMAIL_FORMAT,
  })
  assert.equal(loginFieldErrors('abc', '').email, 'Введите email полностью')
  assert.equal(loginFieldErrors('abc', '').password, 'Введите пароль')
  assert.equal(loginFieldErrors('a@b', 'secret').email, 'Введите email полностью')
})

test('a normal address and password have no field errors', () => {
  assert.deepEqual(loginFieldErrors('captain@kronbars.local', 'DemoPass123!'), {})
})

test('a rejected pair stays under the password and a 5xx or a timeout stays under the button', () => {
  assert.equal(LOGIN_PAIR_INVALID, 'Неверный email или пароль')
  assert.equal(LOGIN_NETWORK, 'Не удалось войти. Повторите позже')
  assert.equal(loginSubmitFailure(null), 'network')
  assert.equal(loginSubmitFailure({}), 'network')
  assert.equal(loginSubmitFailure({ response: null }), 'network')
  assert.equal(loginSubmitFailure({ response: { status: 500 } }), 'network')
  assert.equal(loginSubmitFailure({ response: { status: 503 } }), 'network')
  assert.equal(loginSubmitFailure({ response: { status: 401 } }), 'pair')
  assert.equal(loginSubmitFailure({ response: { status: 400 } }), 'pair')
})
