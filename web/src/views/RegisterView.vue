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

const email = ref('')
const password = ref('')
const roles = ref<Role[]>(['FAN'])
const firstName = ref('')
const lastName = ref('')
const photoUrl = ref('')
const uploadingPhoto = ref(false)
const error = ref('')
const pending = ref(false)
const showPassword = ref(false)
const auth = useAuthStore()
const router = useRouter()

const needsPhoto = computed(() => roles.value.some((role) => role === 'PLAYER' || role === 'CAPTAIN' || role === 'REFEREE'))
const hint = computed(() => passwordHint(password.value))

function hasRole(role: Role) {
  return roles.value.includes(role)
}

function toggleRole(role: Role) {
  if (hasRole(role)) {
    if (roles.value.length === 1) return
    roles.value = roles.value.filter((item) => item !== role)
    return
  }
  roles.value = [...roles.value, role]
}

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  photoUrl.value = ''
  uploadingPhoto.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post('/auth/photo', form)
    photoUrl.value = data.url
  } catch (e: any) {
    error.value = apiError(e, 'Фото не загрузилось. Нужны JPEG, PNG, WebP или GIF, до 8 МБ.')
  } finally {
    uploadingPhoto.value = false
  }
}

async function submit() {
  error.value = ''
  if (needsPhoto.value && !photoUrl.value) {
    error.value = uploadingPhoto.value
      ? 'Подождите, фото ещё загружается.'
      : 'Для игрока, капитана и судьи нужно фото.'
    return
  }
  pending.value = true
  try {
    await auth.register({
      email: email.value,
      password: password.value,
      firstName: firstName.value,
      lastName: lastName.value,
      roles: roles.value,
      role: roles.value[0],
      photoUrl: needsPhoto.value ? photoUrl.value : undefined,
    })
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось зарегистрироваться. Проверьте данные.'
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
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось войти через Google.'
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
      <form class="stack" @submit.prevent="submit">
        <label class="field">Имя
          <input v-model="firstName" required maxlength="100" />
        </label>
        <label class="field">Фамилия
          <input v-model="lastName" required maxlength="100" />
        </label>
        <label class="field">Email
          <input v-model="email" type="email" required autocomplete="username" placeholder="you@league.local" />
        </label>
        <label class="field">Пароль
          <span class="pass-row">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              required
              minlength="8"
              autocomplete="new-password"
            />
            <button class="btn ghost peek" type="button" @click="showPassword = !showPassword">
              {{ showPassword ? 'Скрыть' : 'Показать' }}
            </button>
          </span>
          <span class="field-hint">{{ hint }}</span>
        </label>

        <p class="field-hint">Выберите одну или несколько ролей</p>
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

        <label v-if="needsPhoto" class="field">Фото
          <span class="photo-row">
            <PlayerAvatar
              v-if="photoUrl"
              :src="photoUrl"
              :name="`${lastName} ${firstName}`.trim()"
              :size="56"
              tile
            />
            <span class="grow">
              <input type="file" accept="image/*" @change="onPhoto" />
              <span class="field-hint">{{ photoUrl ? 'Фото загружено' : uploadingPhoto ? 'Загружаем…' : 'JPEG, PNG, WebP или GIF, до 8 МБ' }}</span>
            </span>
          </span>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn success" type="submit" :disabled="pending">
          {{ pending ? 'Создаём…' : 'Создать аккаунт' }}
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
