<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'
import { passwordHint } from '../lib/format'

type Role = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE'

const email = ref('')
const password = ref('')
const role = ref<Role>('FAN')
const firstName = ref('')
const lastName = ref('')
const photoUrl = ref('')
const error = ref('')
const pending = ref(false)
const showPassword = ref(false)
const auth = useAuthStore()
const router = useRouter()

const needsPhoto = computed(() => role.value === 'PLAYER' || role.value === 'CAPTAIN' || role.value === 'REFEREE')
const hint = computed(() => passwordHint(password.value))

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post('/auth/photo', form)
  photoUrl.value = data.url
}

async function submit() {
  error.value = ''
  pending.value = true
  try {
    await auth.register({
      email: email.value,
      password: password.value,
      firstName: firstName.value,
      lastName: lastName.value,
      role: role.value,
      photoUrl: needsPhoto.value ? photoUrl.value : undefined,
    })
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Регистрация споткнулась. Проверьте ФИО, роль и фото.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack card">
      <div class="page-title">
        <p class="eyebrow">Регистрация</p>
        <h1>Студент лиги</h1>
        <p>ФИО и почта обязательны. Игрок, капитан и судья прикладывают фото. Роль подтверждает админ.</p>
      </div>
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

        <div class="role-pick">
          <button type="button" class="role-card" :class="{ active: role === 'FAN' }" @click="role = 'FAN'">
            <strong>Болельщик</strong>
            <span>Смотрит матчи и таблицу. Вкладки «Моя команда» нет.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: role === 'PLAYER' }" @click="role = 'PLAYER'">
            <strong>Игрок</strong>
            <span>После подтверждения админом капитан может взять в состав.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: role === 'CAPTAIN' }" @click="role = 'CAPTAIN'">
            <strong>Капитан</strong>
            <span>Назначает только админ. Здесь — заявка на роль.</span>
          </button>
          <button type="button" class="role-card" :class="{ active: role === 'REFEREE' }" @click="role = 'REFEREE'">
            <strong>Судья</strong>
            <span>Live-протокол после подтверждения и назначения на матч.</span>
          </button>
        </div>

        <label v-if="needsPhoto" class="field">Фото
          <input type="file" accept="image/*" @change="onPhoto" />
          <span class="field-hint">{{ photoUrl ? 'Фото загружено' : 'Обязательно для этой роли' }}</span>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn success" type="submit" :disabled="pending || (needsPhoto && !photoUrl)">
          {{ pending ? 'Печатаем бейдж…' : 'Создать аккаунт' }}
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
}
</style>
