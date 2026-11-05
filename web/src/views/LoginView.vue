<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import GoogleSignInButton from '../components/GoogleSignInButton.vue'
import { useAuthStore } from '../stores/auth'
import { BRAND_MARK_SRC, SITE_NAME } from '../lib/brand'
import { loginFieldErrors, type LoginFieldErrors } from '../lib/loginFields'
import { safeInternalPath } from '../lib/redirect'

const email = ref('')
const password = ref('')
const error = ref('')
const googleError = ref('')
const showPassword = ref(false)
const pending = ref(false)
const fieldErrors = ref<LoginFieldErrors>({})
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const passwordUpdated = computed(() => route.query.reset === '1')

function clearField(key: keyof LoginFieldErrors) {
  if (!fieldErrors.value[key]) return
  const next = { ...fieldErrors.value }
  delete next[key]
  fieldErrors.value = next
}

async function submit() {
  error.value = ''
  googleError.value = ''
  const next = loginFieldErrors(email.value, password.value)
  fieldErrors.value = next
  if (next.email || next.password) return
  pending.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    router.push(safeInternalPath(route.query.redirect))
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Неверный email или пароль.'
  } finally {
    pending.value = false
  }
}

async function onGoogle(idToken: string) {
  error.value = ''
  googleError.value = ''
  pending.value = true
  try {
    await auth.loginWithGoogle(idToken)
    router.push(safeInternalPath(route.query.redirect))
  } catch (e: any) {
    googleError.value = e.response?.data?.message || 'Не удалось войти через Google.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="split">
      <aside class="promo">
        <img class="promo-logo" :src="BRAND_MARK_SRC" alt="" />
        <p class="eyebrow">{{ SITE_NAME }}</p>
      </aside>
      <div class="panel stack card">
        <h2>Вход</h2>
        <form class="stack" novalidate @submit.prevent="submit">
          <label class="field" :class="{ invalid: fieldErrors.email }">
            Email
            <input v-model="email" type="email" maxlength="320" autocomplete="username" @input="clearField('email')" />
            <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
          </label>
          <label class="field" :class="{ invalid: fieldErrors.password }">
            Пароль
            <span class="pass-row">
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                maxlength="100"
                autocomplete="current-password"
                @input="clearField('password')"
              />
              <button class="btn ghost peek" type="button" @click="showPassword = !showPassword">
                {{ showPassword ? 'Скрыть' : 'Показать' }}
              </button>
            </span>
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </label>
          <RouterLink class="forgot" to="/forgot-password">Забыли пароль?</RouterLink>
          <p v-if="passwordUpdated" class="form-ok">Пароль обновлён. Войдите с новым паролем.</p>
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="btn" type="submit" :disabled="pending">
            {{ pending ? 'Входим…' : 'Войти' }}
          </button>
        </form>
        <GoogleSignInButton @success="onGoogle" @error="googleError = $event" />
        <p v-if="googleError" class="form-error">{{ googleError }}</p>
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
.promo-logo {
  width: 92px;
  height: 92px;
  object-fit: contain;
  background: transparent;
}
.promo ul { margin: 0; padding-left: 1.1rem; display: grid; gap: 0.4rem; }
.card { border: 0; box-shadow: none; border-radius: 0; }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
.forgot { justify-self: start; color: var(--navy); font-size: 0.92rem; }
@media (max-width: 760px) { .split { grid-template-columns: 1fr; } }
</style>
