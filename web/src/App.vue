<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="container topbar-inner">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">SL</span>
          <span class="brand-text">Student League</span>
        </RouterLink>
        <nav>
          <RouterLink to="/tournaments">Турниры</RouterLink>
          <RouterLink to="/matches">Матчи</RouterLink>
          <RouterLink to="/teams">Команды</RouterLink>
          <RouterLink to="/players">Игроки</RouterLink>
          <RouterLink to="/statistics">Статистика</RouterLink>
          <RouterLink v-if="auth.role === 'ADMIN'" to="/admin">Админ</RouterLink>
          <RouterLink v-if="auth.role === 'REFEREE' || auth.role === 'ADMIN'" to="/referee">Судья</RouterLink>
        </nav>
        <div class="auth">
          <template v-if="auth.isAuthenticated">
            <span class="user-chip">{{ auth.user?.email }}</span>
            <button class="btn secondary" @click="auth.logout()">Выйти</button>
          </template>
          <template v-else>
            <RouterLink class="btn secondary" to="/login">Войти</RouterLink>
            <RouterLink class="btn" to="/register">Регистрация</RouterLink>
          </template>
        </div>
      </div>
    </header>
    <main class="container main rise">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell { min-height: 100vh; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(16px);
  background: rgba(13, 17, 23, 0.86);
  border-bottom: 1px solid var(--line);
}
.topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 64px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  color: var(--text-strong);
  text-decoration: none;
  font-weight: 700;
}
.brand:hover { text-decoration: none; }
.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent);
  border: 1px solid rgba(88, 166, 255, 0.35);
  font-size: 0.78rem;
  letter-spacing: 0.04em;
}
.brand-text { font-size: 1rem; }
nav { display: flex; gap: 0.15rem; flex-wrap: wrap; }
nav a {
  color: var(--muted);
  font-weight: 600;
  font-size: 0.92rem;
  padding: 0.4rem 0.7rem;
  border-radius: 8px;
  text-decoration: none;
}
nav a:hover { color: var(--text-strong); background: rgba(110, 118, 129, 0.12); text-decoration: none; }
nav a.router-link-active {
  color: var(--text-strong);
  background: var(--accent-soft);
}
.auth { display: flex; gap: 0.55rem; align-items: center; }
.user-chip {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 0.85rem;
}
.main { padding: 1.5rem 0 3rem; }
@media (max-width: 900px) {
  .topbar-inner { flex-wrap: wrap; padding: 0.75rem 0; }
  .user-chip { display: none; }
}
</style>
