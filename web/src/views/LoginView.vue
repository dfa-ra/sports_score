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
    await auth.login(email.value, password.value)
    router.push((router.currentRoute.value.query.redirect as string) || '/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Не удалось войти'
  }
}
</script>

<template>
  <section class="auth-wrap rise">
    <div class="panel stack">
      <div class="page-title">
        <h1>Вход</h1>
        <p>Доступ к Student League</p>
      </div>
      <form class="stack" @submit.prevent="submit">
        <label class="field">Email<input v-model="email" type="email" required autocomplete="username" /></label>
        <label class="field">Пароль<input v-model="password" type="password" required minlength="8" autocomplete="current-password" /></label>
        <p v-if="error" style="color:var(--danger)">{{ error }}</p>
        <button class="btn" type="submit">Войти</button>
      </form>
      <p class="muted">Нет аккаунта? <RouterLink to="/register">Зарегистрироваться</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 140px); }
.panel { width: min(420px, 100%); }
</style>
