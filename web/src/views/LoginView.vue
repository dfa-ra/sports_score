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
    error.value = e.response?.data?.message || 'Не пускает. Проверьте почту и пароль.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="split">
      <aside class="promo">
        <p class="eyebrow">My League</p>
        <h1>Одна лига для игроков, судей и трибуны</h1>
        <ul>
          <li>Смотреть live и таблицу без регистрации</li>
          <li>Играть и судить после подтверждения роли</li>
          <li>Календарь и статистика как на большом сайте</li>
        </ul>
      </aside>
      <div class="panel stack card">
        <h2>Вход</h2>
        <form class="stack" @submit.prevent="submit">
          <label class="field" :class="{ invalid: emailInvalid }">
            Email
            <input v-model="email" type="email" required autocomplete="username" @blur="touched = true" />
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
              />
              <button class="btn ghost peek" type="button" @click="showPassword = !showPassword">
                {{ showPassword ? 'Скрыть' : 'Показать' }}
              </button>
            </span>
          </label>
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="btn" type="submit" :disabled="pending">
            {{ pending ? 'Входим…' : 'Войти' }}
          </button>
        </form>
        <p class="muted">Ещё без аккаунта?</p>
        <RouterLink class="btn secondary" to="/register">Создать аккаунт</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.split {
  width: min(880px, 100%);
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 18px 40px -24px rgba(0, 32, 91, 0.45);
}
.promo {
  background: linear-gradient(180deg, #d9e7f7, #c8efe8);
  padding: 2rem 1.6rem;
  color: var(--navy);
  display: grid;
  align-content: center;
  gap: 0.8rem;
}
.promo h1 { font-size: 1.7rem; }
.promo ul { margin: 0; padding-left: 1.1rem; display: grid; gap: 0.4rem; }
.card { border: 0; box-shadow: none; border-radius: 0; }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
@media (max-width: 760px) { .split { grid-template-columns: 1fr; } }
</style>
