<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { passwordHint } from '../lib/format'

const email = ref('')
const password = ref('')
const accountType = ref<'FAN' | 'PLAYER'>('FAN')
const firstName = ref('')
const lastName = ref('')
const error = ref('')
const pending = ref(false)
const showPassword = ref(false)
const auth = useAuthStore()
const router = useRouter()

const isPlayer = computed(() => accountType.value === 'PLAYER')
const hint = computed(() => passwordHint(password.value))

async function submit() {
  error.value = ''
  pending.value = true
  try {
    await auth.register({
      email: email.value,
      password: password.value,
      accountType: accountType.value,
      firstName: isPlayer.value ? firstName.value : undefined,
      lastName: isPlayer.value ? lastName.value : undefined,
    })
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Регистрация споткнулась. Попробуем ещё раз без фальстарта.'
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
        <h1>Выберите трибуну</h1>
        <p>Зритель смотрит. Игрок выходит на поле. Админа через форму не назначают — так честнее.</p>
      </div>
      <form class="stack" @submit.prevent="submit">
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
          <button
            type="button"
            class="role-card"
            :class="{ active: accountType === 'FAN' }"
            @click="accountType = 'FAN'"
          >
            <strong>Зритель</strong>
            <span>Матчи, турниры и статистика. Можно болеть громко.</span>
          </button>
          <button
            type="button"
            class="role-card"
            :class="{ active: accountType === 'PLAYER' }"
            @click="accountType = 'PLAYER'"
          >
            <strong>Игрок</strong>
            <span>Профиль, команда и заявка на турнир. Бутсы прилагаются мысленно.</span>
          </button>
        </div>

        <div v-if="isPlayer" class="player-fields">
          <label class="field">Имя
            <input v-model="firstName" required maxlength="100" />
          </label>
          <label class="field">Фамилия
            <input v-model="lastName" required maxlength="100" />
          </label>
        </div>

        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn success" type="submit" :disabled="pending">
          {{ pending ? 'Печатаем бейдж…' : 'Создать аккаунт' }}
        </button>
      </form>
      <p class="muted">Уже есть аккаунт? <RouterLink to="/login">Войти</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.card { width: min(480px, 100%); border-radius: 28px 18px 24px 16px; }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
.role-pick { display: grid; gap: 0.7rem; }
.role-card {
  text-align: left;
  display: grid;
  gap: 0.28rem;
  padding: 0.95rem 1rem;
  border-radius: 16px 13px 15px 12px;
  border: 1px solid var(--line);
  background: rgba(10, 13, 8, 0.35);
  color: var(--text);
  cursor: pointer;
  transition: transform 180ms var(--spring), border-color 180ms var(--ease), background-color 180ms var(--ease);
}
.role-card:hover { transform: translateY(-2px); }
.role-card:active { transform: scale(0.98); }
.role-card strong { color: var(--text-strong); font-family: var(--font-display); font-size: 1.15rem; }
.role-card span { color: var(--muted); font-size: 0.88rem; }
.role-card.active {
  border-color: rgba(226, 179, 106, 0.55);
  background: var(--accent-soft);
}
.player-fields {
  display: grid;
  gap: 0.85rem;
  animation: fadeLift 280ms var(--ease) both;
}
</style>
