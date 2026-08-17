<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
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
    error.value = e.response?.data?.message || 'Login failed'
  }
}
</script>

<template>
  <section class="panel stack" style="max-width:420px;margin:2rem auto">
    <h1>Login</h1>
    <p>Access your Student League account.</p>
    <form class="stack" @submit.prevent="submit">
      <label class="field">Email<input v-model="email" type="email" required /></label>
      <label class="field">Password<input v-model="password" type="password" required minlength="8" /></label>
      <p v-if="error" style="color:var(--danger)">{{ error }}</p>
      <button class="btn" type="submit">Sign in</button>
    </form>
  </section>
</template>
