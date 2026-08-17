<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const password = ref('')
const error = ref('')
const showPassword = ref(false)
const pending = ref(false)
const touched = ref(false)
const auth = useAuthStore()
const router = useRouter()

const emailInvalid = computed(() => touched.value && email.value.length > 0 && !email.value.includes('@'))

async function submit() {
  touched.value = true
  error.value = ''
  pending.value = true
  try {
    await auth.login(email.value, password.value)
    router.push((router.currentRoute.value.query.redirect as string) || '/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не пускает. Проверьте почту, пароль и настроение.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack card">
      <div class="page-title">
        <p class="eyebrow">Вход</p>
        <h1>С возвращением</h1>
        <p>Билет на трибуну — это просто почта и пароль. Без турникетов.</p>
      </div>
      <form class="stack" @submit.prevent="submit">
        <label class="field" :class="{ invalid: emailInvalid }">
          Email
          <input
            v-model="email"
            type="email"
            required
            autocomplete="username"
            placeholder="you@league.local"
            @blur="touched = true"
          />
          <span class="field-hint">{{ emailInvalid ? 'Похоже, @ ещё разминается.' : 'Туда придут только нужные свистки.' }}</span>
        </label>
        <label class="field">
          Пароль
          <span class="pass-row">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              required
              minlength="8"
              autocomplete="current-password"
              placeholder="••••••••"
            />
            <button class="btn ghost peek" type="button" @click="showPassword = !showPassword">
              {{ showPassword ? 'Скрыть' : 'Показать' }}
            </button>
          </span>
        </label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn" type="submit" :disabled="pending">
          {{ pending ? 'Открываем калитку…' : 'Войти' }}
        </button>
      </form>
      <p class="muted">Ещё без аккаунта? <RouterLink to="/register">Зарегистрироваться</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.card { width: min(440px, 100%); border-radius: 26px 18px 24px 16px; }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
</style>
