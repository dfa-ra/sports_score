<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { labelOf, roleLabel } from './lib/format'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const mood = computed(() => {
  if (route.path.startsWith('/login') || route.path.startsWith('/register')) return 'mood-auth'
  if (route.path.startsWith('/referee')) return 'mood-ref'
  if (route.path.startsWith('/admin')) return 'mood-admin'
  if (route.path.startsWith('/matches')) return 'mood-pitch'
  return 'mood-home'
})

watch(mood, (value) => {
  document.body.classList.remove('mood-auth', 'mood-pitch', 'mood-ref', 'mood-admin', 'mood-home')
  document.body.classList.add(value)
}, { immediate: true })

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>

<template>
  <div class="shell" :data-mood="mood">
    <header class="topbar">
      <div class="container topbar-inner">
        <RouterLink class="brand" to="/">
          <span class="brand-mark" aria-hidden="true">SL</span>
          <span class="brand-text">
            Student League
            <small>живая студенческая лига</small>
          </span>
        </RouterLink>

        <button class="btn icon menu-btn secondary" type="button" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
          <span>{{ menuOpen ? '✕' : '☰' }}</span>
        </button>

        <nav :class="{ open: menuOpen }">
          <RouterLink to="/" active-class="" exact-active-class="router-link-active">Главная</RouterLink>
          <RouterLink to="/table">Таблица</RouterLink>
          <RouterLink to="/calendar">Календарь</RouterLink>
          <RouterLink to="/statistics">Статистика</RouterLink>
          <RouterLink to="/players">Игроки</RouterLink>
          <RouterLink v-if="auth.canAccessMyTeam" to="/my-team">Моя команда</RouterLink>
          <RouterLink v-if="auth.canManageLeague" to="/admin">Админ</RouterLink>
          <RouterLink v-if="auth.canOfficiate" to="/referee">Судья</RouterLink>
          <RouterLink v-if="auth.isAuthenticated" to="/profile">Профиль</RouterLink>
        </nav>

        <div class="auth">
          <template v-if="auth.isAuthenticated">
            <span class="user-chip">
              <em>{{ labelOf(roleLabel, auth.role) }}</em>
              {{ auth.user?.email }}
            </span>
            <button class="btn secondary" @click="logout">Выйти</button>
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
    <footer class="foot">
      <div class="container">
        Смотреть можно без билета. Играть — после регистрации.
      </div>
    </footer>
  </div>
</template>

<style scoped>
.shell { min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(18px);
  background: color-mix(in srgb, var(--navy) 82%, transparent);
  border-bottom: 1px solid var(--line);
}
.topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 72px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.7rem;
  color: var(--text-strong);
  text-decoration: none;
}
.brand:hover { text-decoration: none; color: var(--text-strong); }
.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px 10px 14px 9px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent);
  border: 1px solid rgba(76, 180, 229, 0.4);
  font-family: var(--font-display);
  font-size: 0.92rem;
  transform: rotate(-4deg);
  transition: transform 220ms var(--spring);
}
.brand:hover .brand-mark { transform: rotate(3deg) scale(1.06); }
.brand-text {
  display: grid;
  font-family: var(--font-display);
  font-weight: 800;
  line-height: 1.05;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.brand-text small {
  color: var(--muted);
  font-weight: 600;
  font-size: 0.68rem;
  letter-spacing: 0.04em;
}
nav { display: flex; gap: 0.12rem; flex-wrap: wrap; }
nav a {
  color: var(--muted);
  font-weight: 700;
  font-size: 0.9rem;
  padding: 0.42rem 0.72rem;
  border-radius: 999px;
  text-decoration: none;
  transition: transform 180ms var(--spring), background-color 180ms var(--ease), color 180ms var(--ease);
}
nav a:hover { color: var(--text-strong); background: rgba(255, 255, 255, 0.05); transform: translateY(-1px); }
nav a.router-link-active {
  color: var(--navy);
  background: var(--accent);
}
.auth { display: flex; gap: 0.55rem; align-items: center; }
.user-chip {
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 0.82rem;
}
.user-chip em {
  display: block;
  font-style: normal;
  color: var(--accent);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 800;
}
.menu-btn { display: none; }
.main { padding: 1.6rem 0 2.4rem; }
.foot {
  color: var(--muted);
  font-size: 0.78rem;
  padding: 0 0 1.4rem;
  opacity: 0.8;
}
@media (max-width: 980px) {
  .menu-btn { display: inline-flex; }
  nav {
    display: none;
    width: 100%;
    order: 4;
    padding-bottom: 0.6rem;
  }
  nav.open { display: flex; }
  .topbar-inner { flex-wrap: wrap; padding: 0.7rem 0; }
  .user-chip { display: none; }
}
</style>
