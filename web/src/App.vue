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
    <header class="chrome">
      <div class="util">
        <div class="container util-inner">
          <span>KRONBARS · студенческая лига</span>
          <span v-if="auth.isAuthenticated">{{ labelOf(roleLabel, auth.role) }}</span>
        </div>
      </div>
      <div class="nav-bar">
        <div class="container nav-inner">
          <RouterLink class="brand" to="/">
            <span class="brand-mark" aria-hidden="true">SL</span>
            <span>Student League</span>
          </RouterLink>

          <button class="btn icon menu-btn secondary" type="button" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
            <span>{{ menuOpen ? '✕' : '☰' }}</span>
          </button>

          <nav :class="{ open: menuOpen }">
            <RouterLink to="/" exact-active-class="on">Главная</RouterLink>
            <RouterLink to="/table" active-class="on">Таблица</RouterLink>
            <RouterLink to="/calendar" active-class="on">Календарь</RouterLink>
            <RouterLink to="/statistics" active-class="on">Статистика</RouterLink>
            <RouterLink to="/players" active-class="on">Игроки</RouterLink>
            <RouterLink v-if="auth.canAccessMyTeam" to="/my-team" active-class="on">Моя команда</RouterLink>
            <RouterLink v-if="auth.canManageLeague" to="/admin" active-class="on">Админ</RouterLink>
            <RouterLink v-if="auth.canOfficiate" to="/referee" active-class="on">Судья</RouterLink>
          </nav>

          <div class="auth">
            <template v-if="auth.isAuthenticated">
              <RouterLink class="login-pill" to="/profile">Профиль</RouterLink>
              <button class="login-pill ghost" type="button" @click="logout">Выйти</button>
            </template>
            <template v-else>
              <RouterLink class="login-pill" to="/login">Войти</RouterLink>
            </template>
          </div>
        </div>
      </div>
    </header>
    <main class="main" :class="{ flush: route.path === '/' }">
      <div :class="route.path === '/' ? 'home-bleed' : 'container rise'">
        <RouterView />
      </div>
    </main>
    <footer class="foot">
      <div class="container">Смотреть можно без билета. Играть — после регистрации.</div>
    </footer>
  </div>
</template>

<style scoped>
.shell { min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; }
.chrome { background: var(--navy); color: #fff; }
.util { background: #00143d; font-size: 0.72rem; letter-spacing: 0.04em; text-transform: uppercase; }
.util-inner { display: flex; justify-content: space-between; min-height: 32px; align-items: center; color: rgba(255,255,255,0.7); }
.nav-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 68px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  color: #fff;
  text-decoration: none;
  font-family: var(--font-display);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.brand:hover { color: #fff; text-decoration: none; }
.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ice);
  color: var(--navy);
  font-size: 0.85rem;
}
nav { display: flex; gap: 0.15rem; flex-wrap: wrap; }
nav a {
  color: rgba(255,255,255,0.82);
  font-weight: 700;
  font-size: 0.88rem;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  text-decoration: none;
}
nav a:hover { color: #fff; background: rgba(255,255,255,0.08); }
nav a.on { color: var(--navy); background: var(--ice); }
.login-pill {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(255,255,255,0.45);
  color: #fff;
  border-radius: 999px;
  padding: 0.38rem 0.9rem;
  text-decoration: none;
  background: transparent;
  font-weight: 700;
  cursor: pointer;
}
.login-pill:hover { background: rgba(255,255,255,0.08); color: #fff; }
.login-pill.ghost { border-color: transparent; color: rgba(255,255,255,0.75); }
.auth { display: flex; gap: 0.4rem; align-items: center; }
.menu-btn { display: none; }
.main { padding: 1.4rem 0 2.4rem; }
.main.flush { padding: 0 0 2.4rem; }
.foot { color: var(--muted); font-size: 0.78rem; padding: 0 0 1.4rem; }
@media (max-width: 980px) {
  .menu-btn { display: inline-flex; }
  nav { display: none; width: 100%; order: 4; padding-bottom: 0.7rem; }
  nav.open { display: flex; }
  .nav-inner { flex-wrap: wrap; }
}
</style>
