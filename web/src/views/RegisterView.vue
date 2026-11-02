<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import GoogleSignInButton from '../components/GoogleSignInButton.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'
import { passwordHint } from '../lib/format'
import { apiError } from '../lib/errors'
import { googleClientId } from '../lib/googleIdentity'

type Role = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE'

type FieldKey = 'firstName' | 'lastName' | 'email' | 'password' | 'roles' | 'photo'

const email = ref('')
const password = ref('')
const roles = ref<Role[]>(['FAN'])
const firstName = ref('')
const lastName = ref('')
const photoUrl = ref('')
const uploadingPhoto = ref(false)
const error = ref('')
const fieldErrors = ref<Partial<Record<FieldKey, string>>>({})
const pending = ref(false)
const showPassword = ref(false)
const auth = useAuthStore()
const router = useRouter()

const needsPhoto = computed(() => roles.value.some((role) => role === 'PLAYER' || role === 'CAPTAIN' || role === 'REFEREE'))
const hint = computed(() => passwordHint(password.value))
const submitLocked = computed(() => pending.value || uploadingPhoto.value)

function hasRole(role: Role) {
  return roles.value.includes(role)
}

function toggleRole(role: Role) {
  if (hasRole(role)) {
    if (roles.value.length === 1) return
    roles.value = roles.value.filter((item) => item !== role)
  } else {
    roles.value = [...roles.value, role]
  }
  clearField('roles')
  if (!needsPhoto.value) clearField('photo')
}

function clearField(key: FieldKey) {
  if (!fieldErrors.value[key]) return
  const next = { ...fieldErrors.value }
  delete next[key]
  fieldErrors.value = next
}

function looksLikeEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function validate() {
  const next: Partial<Record<FieldKey, string>> = {}
  const first = firstName.value.trim()
  const last = lastName.value.trim()
  const mail = email.value.trim()
  if (!first) next.firstName = 'Укажите имя'
  else if (first.length > 100) next.firstName = 'Имя не длиннее 100 символов'
  if (!last) next.lastName = 'Укажите фамилию'
  else if (last.length > 100) next.lastName = 'Фамилия не длиннее 100 символов'
  if (!mail) next.email = 'Укажите почту'
  else if (mail.length > 320 || !looksLikeEmail(mail)) next.email = 'Укажите корректную почту'
  if (!password.value) next.password = 'Укажите пароль'
  else if (password.value.length < 8 || password.value.length > 100) {
    next.password = 'Пароль должен быть от 8 до 100 символов'
  }
  if (!roles.value.length) next.roles = 'Выберите хотя бы одну роль'
  if (needsPhoto.value && !photoUrl.value) {
    next.photo = uploadingPhoto.value
      ? 'Подождите, фото ещё загружается.'
      : 'Для игрока, капитана и судьи нужна фотография'
  }
  fieldErrors.value = next
  return Object.keys(next).length === 0
}

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploadingPhoto.value || pending.value) return
  error.value = ''
  clearField('photo')
  photoUrl.value = ''
  uploadingPhoto.value = true
  try {
    const { data: ticket } = await api.post('/auth/photo-ticket')
    const form = new FormData()
    form.append('file', file)
    form.append('token', ticket.token)
    const { data } = await api.post('/auth/photo', form)
    photoUrl.value = data.url
    clearField('photo')
  } catch (e: unknown) {
    error.value = apiError(e, 'Фото не загрузилось. Нужны JPEG, PNG, WebP или GIF, до 8 МБ.')
  } finally {
    uploadingPhoto.value = false
  }
}

async function submit() {
  if (pending.value || uploadingPhoto.value) return
  error.value = ''
  if (!validate()) return
  pending.value = true
  try {
    await auth.register({
      email: email.value.trim(),
      password: password.value,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      roles: roles.value,
      role: roles.value[0],
      photoUrl: needsPhoto.value ? photoUrl.value : undefined,
    })
    router.push('/')
  } catch (e: unknown) {
    error.value = apiError(e, 'Не удалось зарегистрироваться. Проверьте данные.')
  } finally {
    pending.value = false
  }
}

async function onGoogle(idToken: string) {
  error.value = ''
  pending.value = true
  try {
    await auth.loginWithGoogle(idToken)
    router.push('/')
  } catch (e: unknown) {
    error.value = apiError(e, 'Не удалось войти через Google.')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack card">
      <div class="page-title">
        <h1>Регистрация</h1>
        <p>ФИО и почта обязательны. Можно выбрать несколько ролей. Игрок, капитан и судья прикладывают фото. Роли подтверждает админ.</p>
      </div>
      <GoogleSignInButton @success="onGoogle" @error="error = $event" />
      <p v-if="googleClientId" class="muted">Войти через Google. Новый аккаунт будет болельщиком. Игрок, капитан и судья регистрируются формой ниже — для них нужно фото.</p>
      <form class="stack" novalidate @submit.prevent="submit">
        <label class="field" :class="{ invalid: fieldErrors.firstName }">Имя
          <input v-model="firstName" maxlength="100" autocomplete="given-name" @input="clearField('firstName')" />
          <span v-if="fieldErrors.firstName" class="field-error">{{ fieldErrors.firstName }}</span>
        </label>
        <label class="field" :class="{ invalid: fieldErrors.lastName }">Фамилия
          <input v-model="lastName" maxlength="100" autocomplete="family-name" @input="clearField('lastName')" />
          <span v-if="fieldErrors.lastName" class="field-error">{{ fieldErrors.lastName }}</span>
        </label>
        <label class="field" :class="{ invalid: fieldErrors.email }">Email
          <input v-model="email" type="email" maxlength="320" autocomplete="username" placeholder="you@league.local" @input="clearField('email')" />
          <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
        </label>
        <label class="field" :class="{ invalid: fieldErrors.password }">Пароль
          <span class="pass-row">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              maxlength="100"
              autocomplete="new-password"
              @input="clearField('password')"
            />
            <button class="btn ghost peek" type="button" @click="showPassword = !showPassword">
              {{ showPassword ? 'Скрыть' : 'Показать' }}
            </button>
          </span>
          <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          <span v-else class="field-hint">{{ hint }}</span>
        </label>

        <p class="field-hint">Выберите одну или несколько ролей</p>
        <span v-if="fieldErrors.roles" class="field-error">{{ fieldErrors.roles }}</span>
        <div class="role-pick" role="group" aria-label="Роли">
          <button type="button" class="role-card" :class="{ active: hasRole('FAN') }" :aria-pressed="hasRole('FAN')" @click="toggleRole('FAN')">
            <strong>Болельщик</strong>
            <span>Смотрит матчи и таблицу. Вкладки «Моя команда» нет.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: hasRole('PLAYER') }" :aria-pressed="hasRole('PLAYER')" @click="toggleRole('PLAYER')">
            <strong>Игрок</strong>
            <span>После подтверждения админом капитан может взять в состав.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: hasRole('CAPTAIN') }" :aria-pressed="hasRole('CAPTAIN')" @click="toggleRole('CAPTAIN')">
            <strong>Капитан</strong>
            <span>Назначает только админ. Здесь — заявка на роль.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: hasRole('REFEREE') }" :aria-pressed="hasRole('REFEREE')" @click="toggleRole('REFEREE')">
            <strong>Судья</strong>
            <span>Live-протокол после подтверждения и назначения на матч.</span>
          </button>
        </div>

        <label v-if="needsPhoto" class="field" :class="{ invalid: fieldErrors.photo }">Фото
          <span class="photo-row">
            <PlayerAvatar
              v-if="photoUrl"
              :src="photoUrl"
              :name="`${lastName} ${firstName}`.trim()"
              :size="56"
              tile
            />
            <span class="grow">
              <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" :disabled="submitLocked" @change="onPhoto" />
              <span v-if="fieldErrors.photo" class="field-error">{{ fieldErrors.photo }}</span>
              <span v-else class="field-hint">{{ photoUrl ? 'Фото загружено' : uploadingPhoto ? 'Загружаем…' : 'JPEG, PNG, WebP или GIF, до 8 МБ' }}</span>
            </span>
          </span>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn success" type="submit" :disabled="submitLocked" :aria-busy="submitLocked">
          <span v-if="submitLocked" class="spinner" aria-hidden="true"></span>
          {{ pending ? 'Создаём…' : uploadingPhoto ? 'Загружаем фото…' : 'Создать аккаунт' }}
        </button>
      </form>
      <p class="muted">Уже есть аккаунт? <RouterLink to="/login">Войти</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.card { width: min(520px, 100%); border-radius: 28px 18px 24px 16px; }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
.role-pick { display: grid; gap: 0.7rem; }
.role-card {
  text-align: left;
  display: grid;
  gap: 0.28rem;
  padding: 0.95rem 1rem;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: #f6f9fc;
  color: var(--text);
  cursor: pointer;
}
.role-card strong { color: var(--text-strong); font-family: var(--font-display); font-size: 1.15rem; }
.role-card span { color: var(--muted); font-size: 0.88rem; }
.role-card.active {
  border-color: rgba(76, 180, 229, 0.55);
  background: var(--accent-soft);
  box-shadow: inset 0 0 0 1px rgba(76, 180, 229, 0.35);
}
.role-card.active strong::after {
  content: " ✓";
  color: var(--ice);
}
.photo-row { display: flex; align-items: center; gap: 0.75rem; }
.grow { flex: 1; min-width: 0; display: grid; gap: 0.35rem; }
.grow input { width: 100%; }
</style>
