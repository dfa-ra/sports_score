import assert from 'node:assert/strict'
import test from 'node:test'
import {
  LOGIN_EMAIL_FORMAT,
  LOGIN_EMAIL_REQUIRED,
  LOGIN_PASSWORD_REQUIRED,
  loginFieldErrors,
} from '../src/lib/loginFields.ts'

test('an empty login asks for e-mail and password in the register wording', () => {
  assert.deepEqual(loginFieldErrors('', ''), {
    email: LOGIN_EMAIL_REQUIRED,
    password: LOGIN_PASSWORD_REQUIRED,
  })
  assert.deepEqual(loginFieldErrors('   ', ''), {
    email: 'Введите e-mail',
    password: 'Введите пароль',
  })
})

test('e-mail «abc» is a format error and does not replace a filled password', () => {
  assert.deepEqual(loginFieldErrors('abc', 'secret'), {
    email: LOGIN_EMAIL_FORMAT,
  })
  assert.equal(loginFieldErrors('abc', '').email, 'Неверный формат e-mail')
  assert.equal(loginFieldErrors('abc', '').password, 'Введите пароль')
  assert.equal(loginFieldErrors('a@b', 'secret').email, 'Неверный формат e-mail')
})

test('a normal address and password have no field errors', () => {
  assert.deepEqual(loginFieldErrors('captain@kronbars.local', 'DemoPass123!'), {})
})
