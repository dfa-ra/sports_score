<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="container topbar-inner">
        <RouterLink class="brand" to="/">Student League</RouterLink>
        <nav>
          <RouterLink to="/tournaments">Tournaments</RouterLink>
          <RouterLink to="/matches">Matches</RouterLink>
          <RouterLink to="/teams">Teams</RouterLink>
          <RouterLink to="/players">Players</RouterLink>
          <RouterLink to="/statistics">Stats</RouterLink>
          <RouterLink v-if="auth.role === 'ADMIN'" to="/admin">Admin</RouterLink>
          <RouterLink v-if="auth.role === 'REFEREE' || auth.role === 'ADMIN'" to="/referee">Referee</RouterLink>
        </nav>
        <div class="auth">
          <template v-if="auth.isAuthenticated">
            <span class="muted">{{ auth.user?.email }}</span>
            <button class="btn secondary" @click="auth.logout()">Logout</button>
          </template>
          <template v-else>
            <RouterLink class="btn secondary" to="/login">Login</RouterLink>
            <RouterLink class="btn" to="/register">Join</RouterLink>
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
  position: sticky; top: 0; z-index: 20;
  backdrop-filter: blur(14px);
  background: rgba(7, 21, 15, 0.72);
  border-bottom: 1px solid var(--line);
}
.topbar-inner {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  min-height: 72px;
}
.brand {
  font-family: var(--font-display);
  font-size: 1.8rem;
  letter-spacing: 0.06em;
  color: var(--accent);
}
nav { display: flex; gap: 1rem; flex-wrap: wrap; }
nav a { color: var(--muted); font-weight: 600; }
nav a.router-link-active { color: var(--text); }
.auth { display: flex; gap: 0.6rem; align-items: center; }
.main { padding: 1.5rem 0 3rem; }
@media (max-width: 900px) {
  .topbar-inner { flex-wrap: wrap; padding: 0.75rem 0; }
}
</style>
