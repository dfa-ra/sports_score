<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const error = ref('')
const pending = ref(false)
const sent = ref(false)
const auth = useAuthStore()

async function submit() {
  error.value = ''
  pending.value = true
  try {
    await auth.requestPasswordReset(email.value.trim())
    sent.value = true
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось отправить ссылку. Попробуйте ещё раз.'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack card">
      <div class="page-title">
        <h1>Восстановление пароля</h1>
        <p>Укажите почту. Если аккаунт есть, придёт ссылка для нового пароля. Она действует 1 час.</p>
        <p>Если входили через Google и пароля ещё нет, по ссылке его можно задать и дальше входить по почте.</p>
      </div>
      <p v-if="sent" class="form-ok">Если аккаунт с этой почтой есть, мы отправили ссылку для нового пароля. Она действует 1 час.</p>
      <form v-else class="stack" @submit.prevent="submit">
        <label class="field">Email
          <input v-model="email" type="email" required autocomplete="username" />
        </label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn" type="submit" :disabled="pending">
          {{ pending ? 'Отправляем…' : 'Отправить ссылку' }}
        </button>
      </form>
      <RouterLink to="/login">Вернуться ко входу</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 170px); }
.card { width: min(520px, 100%); }
</style>
