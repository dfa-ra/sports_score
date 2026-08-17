<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const password = ref('')
const error = ref('')
const auth = useAuthStore()
const router = useRouter()

async function submit() {
  error.value = ''
  try {
    await auth.register(email.value, password.value)
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось зарегистрироваться'
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack">
      <div class="page-title">
        <h1>Регистрация</h1>
        <p>Создайте FAN-аккаунт, затем оформите профиль игрока.</p>
      </div>
      <form class="stack" @submit.prevent="submit">
        <label class="field">Email<input v-model="email" type="email" required autocomplete="username" /></label>
        <label class="field">Пароль<input v-model="password" type="password" required minlength="8" autocomplete="new-password" /></label>
        <p v-if="error" style="color:var(--danger)">{{ error }}</p>
        <button class="btn success" type="submit">Создать аккаунт</button>
      </form>
      <p class="muted">Уже есть аккаунт? <RouterLink to="/login">Войти</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 140px); }
.panel { width: min(420px, 100%); }
</style>
