import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { APPROVE_BUTTON_LABEL, entryActionError, showApproveButton } from '../src/lib/tournamentEntryActions.ts'

const view = readFileSync(new URL('../src/views/TournamentDetailView.vue', import.meta.url), 'utf8')

test('approve is offered only for a pending entry', () => {
  assert.equal(showApproveButton('PENDING'), true)
  assert.equal(showApproveButton('APPROVED'), false)
  assert.equal(showApproveButton('REJECTED'), false)
  assert.equal(showApproveButton('WITHDRAWN'), false)
  assert.equal(showApproveButton(null), false)
  assert.equal(APPROVE_BUTTON_LABEL, 'Одобрить')
})

test('a 409 from approve or restore is the error text', () => {
  const message = 'Заявку можно одобрить только в статусе «На рассмотрении»'
  assert.equal(entryActionError({
    response: { status: 409, data: { message } },
  }), message)
  assert.equal(entryActionError({
    response: { status: 409, data: { message: 'Вернуть можно только снятую заявку' } },
  }), 'Вернуть можно только снятую заявку')
  assert.equal(entryActionError({ response: { status: 409, data: {} } }), 'Не вышло. Попробуйте ещё раз.')
  assert.equal(entryActionError(null), 'Не вышло. Попробуйте ещё раз.')
})

test('the admin view renders «Одобрить» only for pending and shows the caught error', () => {
  assert.match(view, /v-if="showApproveButton\(team\.status\)"/)
  assert.match(view, /\{\{ APPROVE_BUTTON_LABEL \}\}/)
  assert.doesNotMatch(view, /Допустить/)
  assert.doesNotMatch(view, /status !== 'APPROVED'/)
  assert.match(view, /error\.value = entryActionError\(e\)/)
  assert.equal(view.match(/error\.value = entryActionError\(e\)/g)?.length, 2)
  assert.match(view, /<p v-if="error" class="form-error">\{\{ error \}\}<\/p>/)
})
