import assert from 'node:assert/strict'
import test from 'node:test'
import { ADMIN_LEGACY_PATHS, adminLegacyTarget, adminTabFromQuery } from './adminTabs.ts'

test('legacy admin paths open the tab that already holds that screen', () => {
  assert.deepEqual(adminLegacyTarget('/admin/users'), { path: '/admin', query: { tab: 'users' } })
  assert.deepEqual(adminLegacyTarget('/admin/referees'), { path: '/admin', query: { tab: 'referees' } })
  assert.deepEqual(adminLegacyTarget('/admin/role-requests'), { path: '/admin', query: { tab: 'users' } })
  assert.deepEqual(adminLegacyTarget('/admin/gallery'), { path: '/admin', query: { tab: 'gallery' } })
  assert.deepEqual(adminLegacyTarget('/admin/gallery/vk-album'), { path: '/admin', query: { tab: 'gallery' } })
  assert.deepEqual(adminLegacyTarget('/admin/gallery/vk-album/'), { path: '/admin', query: { tab: 'gallery' } })
  assert.equal(adminLegacyTarget('/admin'), null)
  assert.deepEqual(Object.keys(ADMIN_LEGACY_PATHS), [
    '/admin/users',
    '/admin/referees',
    '/admin/role-requests',
    '/admin/gallery',
    '/admin/gallery/vk-album',
  ])
})

test('an unknown or empty admin tab query stays on users', () => {
  assert.equal(adminTabFromQuery(undefined), 'users')
  assert.equal(adminTabFromQuery(''), 'users')
  assert.equal(adminTabFromQuery('nope'), 'users')
  assert.equal(adminTabFromQuery(['gallery', 'users']), 'gallery')
  assert.equal(adminTabFromQuery('referees'), 'referees')
})
