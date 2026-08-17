<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const password = ref('')
const accountType = ref<'FAN' | 'PLAYER'>('FAN')
const firstName = ref('')
const lastName = ref('')
const error = ref('')
const auth = useAuthStore()
const router = useRouter()

const isPlayer = computed(() => accountType.value === 'PLAYER')

async function submit() {
  error.value = ''
  try {
    await auth.register({
      email: email.value,
      password: password.value,
      accountType: accountType.value,
      firstName: isPlayer.value ? firstName.value : undefined,
      lastName: isPlayer.value ? lastName.value : undefined,
    })
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
        <p>Выберите: зритель или игрок. Админ создаётся только через .env.</p>
      </div>
      <form class="stack" @submit.prevent="submit">
        <label class="field">Email
          <input v-model="email" type="email" required autocomplete="username" />
        </label>
        <label class="field">Пароль
          <input v-model="password" type="password" required minlength="8" autocomplete="new-password" />
        </label>

        <div class="role-pick">
          <button
            type="button"
            class="role-card"
            :class="{ active: accountType === 'FAN' }"
            @click="accountType = 'FAN'"
          >
            <strong>Зритель</strong>
            <span>Смотреть матчи, турниры и статистику</span>
          </button>
          <button
            type="button"
            class="role-card"
            :class="{ active: accountType === 'PLAYER' }"
            @click="accountType = 'PLAYER'"
          >
            <strong>Игрок</strong>
            <span>Профиль, команды, заявки на турниры</span>
          </button>
        </div>

        <template v-if="isPlayer">
          <label class="field">Имя
            <input v-model="firstName" required maxlength="100" />
          </label>
          <label class="field">Фамилия
            <input v-model="lastName" required maxlength="100" />
          </label>
        </template>

        <p v-if="error" style="color:var(--danger)">{{ error }}</p>
        <button class="btn success" type="submit">Создать аккаунт</button>
      </form>
      <p class="muted">Уже есть аккаунт? <RouterLink to="/login">Войти</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.auth-wrap { display: grid; place-items: center; min-height: calc(100vh - 140px); }
.panel { width: min(460px, 100%); }
.role-pick { display: grid; gap: 0.65rem; }
.role-card {
  text-align: left;
  display: grid;
  gap: 0.25rem;
  padding: 0.85rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--bg);
  color: var(--text);
  cursor: pointer;
}
.role-card strong { color: var(--text-strong); }
.role-card span { color: var(--muted); font-size: 0.88rem; }
.role-card.active {
  border-color: rgba(88, 166, 255, 0.55);
  background: var(--accent-soft);
}
</style>
