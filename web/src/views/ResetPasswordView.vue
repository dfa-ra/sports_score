<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { passwordHint } from '../lib/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const pending = ref(false)
const hint = computed(() => passwordHint(password.value))
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))

async function submit() {
  error.value = ''
  if (!token.value) {
    error.value = 'Ссылка недействительна или устарела.'
    return
  }
  if (password.value.length < 8) {
    error.value = 'Минимум 8 символов.'
    return
  }
  pending.value = true
  try {
    await auth.resetPassword(token.value, password.value)
    auth.logoutLocal()
    router.push({ name: 'login', query: { reset: '1' } })
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Ссылка недействительна или устарела.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack card">
      <div class="page-title">
        <h1>Новый пароль</h1>
        <p>Минимум 8 символов. После сохранения войдите с новым паролем.</p>
      </div>
      <p v-if="!token" class="form-error">Ссылка недействительна или устарела.</p>
      <form v-else class="stack" @submit.prevent="submit">
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
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn" type="submit" :disabled="pending">
          {{ pending ? 'Сохраняем…' : 'Сохранить пароль' }}
        </button>
      </form>
      <RouterLink to="/login">Вернуться ко входу</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.card { width: min(520px, 100%); }
.pass-row { display: grid; grid-template-columns: 1fr auto; gap: 0.4rem; }
.peek { min-width: 92px; }
</style>
