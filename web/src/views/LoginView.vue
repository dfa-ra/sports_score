<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PasswordPeek from '../components/PasswordPeek.vue'
import GoogleSignInButton from '../components/GoogleSignInButton.vue'
import { useAuthStore } from '../stores/auth'
import { BRAND_MARK_SRC, SITE_NAME } from '../lib/brand'
import {
  LOGIN_NETWORK,
  LOGIN_PAIR_INVALID,
  loginFieldErrors,
  loginSubmitFailure,
  type LoginFieldErrors,
} from '../lib/loginFields'
import { setPageMeta } from '../lib/pageMeta'
import { safeInternalPath } from '../lib/redirect'

const email = ref('')
const password = ref('')
const networkError = ref('')
const googleError = ref('')
const showPassword = ref(false)
const submitting = ref(false)
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
  networkError.value = ''
  googleError.value = ''
  const next = loginFieldErrors(email.value, password.value)
  fieldErrors.value = next
  if (next.email || next.password) return
  submitting.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    router.push(safeInternalPath(route.query.redirect))
  } catch (e: unknown) {
    if (loginSubmitFailure(e as { response?: { status?: number } | null }) === 'network') {
      networkError.value = LOGIN_NETWORK
    } else {
      fieldErrors.value = { password: LOGIN_PAIR_INVALID }
    }
  } finally {
    submitting.value = false
  }
}

async function onGoogle(idToken: string) {
  networkError.value = ''
  googleError.value = ''
  try {
    await auth.loginWithGoogle(idToken)
    router.push(safeInternalPath(route.query.redirect))
  } catch (e: any) {
    googleError.value = e.response?.data?.message || 'Не удалось войти через Google.'
  }
}

onMounted(() => {
  setPageMeta({ title: 'Вход', description: 'Лига ИТМО: вход' })
})
</script>

<template>
  <section class="auth-wrap">
    <div class="split">
      <aside class="promo">
        <img class="promo-logo" :src="BRAND_MARK_SRC" alt="" />
        <div>
          <strong>{{ SITE_NAME }}</strong>
          <span>Футзал и студенческие турниры</span>
        </div>
      </aside>
      <div class="card">
        <h1>Вход</h1>
        <form novalidate @submit.prevent="submit">
          <label class="field" :class="{ invalid: fieldErrors.email }">
            <span class="lbl">Email</span>
            <input
              v-model="email"
              type="email"
              maxlength="320"
              autocomplete="username"
              placeholder="name@niuitmo.ru"
              :aria-invalid="fieldErrors.email ? 'true' : 'false'"
              @input="clearField('email')"
            />
            <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
          </label>
          <label class="field" :class="{ invalid: fieldErrors.password }">
            <span class="lbl">Пароль</span>
            <PasswordPeek
              v-model="password"
              v-model:visible="showPassword"
              autocomplete="current-password"
              placeholder="Пароль"
              @input="clearField('password')"
            />
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </label>
          <RouterLink class="forgot" to="/forgot-password">Забыли пароль?</RouterLink>
          <p v-if="passwordUpdated" class="form-ok">Пароль обновлён. Войдите с новым паролем.</p>
          <button class="btn" type="submit" :disabled="submitting" :aria-busy="submitting">
            <span v-if="submitting" class="spinner" aria-hidden="true" />
            {{ submitting ? 'Входим…' : 'Войти' }}
          </button>
          <p v-if="networkError" class="net-error">{{ networkError }}</p>
        </form>
        <p class="or"><span>или</span></p>
        <GoogleSignInButton labeled @success="onGoogle" @error="googleError = $event" />
        <p v-if="googleError" class="net-error">{{ googleError }}</p>
        <p class="create">Нет аккаунта? <RouterLink to="/register">Создать</RouterLink></p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); min-width: 0; }
.split {
  width: min(880px, 100%);
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 18px 40px -24px rgba(0, 32, 91, 0.45);
}
.promo {
  background: #001433;
  color: #fff;
  padding: 2rem 1.5rem;
  display: grid;
  justify-items: center;
  align-content: center;
  text-align: center;
  gap: 0.8rem;
}
.promo-logo { width: 92px; height: 92px; object-fit: contain; }
.promo strong { display: block; font-family: var(--font-display); font-size: 1.35rem; letter-spacing: 0.03em; }
.promo span { display: block; margin-top: 0.25rem; color: #c5d4ea; font-size: 0.85rem; }
.card { padding: 1.6rem 1.4rem 1.3rem; display: grid; align-content: start; gap: 0.85rem; min-width: 0; }
.card h1 { margin: 0; font-size: 1.8rem; }
form { display: grid; gap: 0.7rem; min-width: 0; }
.lbl { font-size: 0.72rem; letter-spacing: 0.06em; text-transform: uppercase; color: #7b8798; }
.card :deep(input) { border-color: #9fd4ee; border-radius: 12px; width: 100%; }
.forgot { justify-self: end; color: #2f8ec4; font-size: 0.85rem; }
.btn { width: 100%; }
.net-error { margin: 0; color: var(--danger); font-size: 0.85rem; }
.or { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 0.6rem; margin: 0; color: #8b97a8; font-size: 0.8rem; }
.or::before, .or::after { content: ''; height: 1px; background: #e1e8f0; }
.create { margin: 0; text-align: center; color: #5d6b7c; }
.create a { color: #2f8ec4; font-weight: 700; }
@media (max-width: 720px) {
  .auth-wrap { min-height: 0; }
  .split {
    grid-template-columns: 1fr;
    background: transparent;
    box-shadow: none;
    border-radius: 0;
    overflow: visible;
    gap: 0.75rem;
  }
  .promo {
    grid-template-columns: auto minmax(0, 1fr);
    justify-items: start;
    align-items: center;
    text-align: left;
    border-radius: 16px;
    padding: 0.75rem 0.85rem;
    gap: 0.7rem;
  }
  .promo-logo { width: 42px; height: 42px; }
  .promo strong { font-size: 0.98rem; }
  .promo span { font-size: 0.72rem; }
  .card { background: #fff; border-radius: 16px; padding: 1.05rem 0.95rem 1rem; }
}
</style>
