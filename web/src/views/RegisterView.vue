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
    await auth.register(email.value, password.value)
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Registration failed'
  }
}
</script>

<template>
  <section class="panel stack" style="max-width:420px;margin:2rem auto">
    <h1>Join the League</h1>
    <p>Create a FAN account, then build your player profile.</p>
    <form class="stack" @submit.prevent="submit">
      <label class="field">Email<input v-model="email" type="email" required /></label>
      <label class="field">Password<input v-model="password" type="password" required minlength="8" /></label>
      <p v-if="error" style="color:var(--danger)">{{ error }}</p>
      <button class="btn" type="submit">Create account</button>
    </form>
  </section>
</template>
