<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import PlayerAvatar from './components/PlayerAvatar.vue'
import { appVersionLabel, appVersionTitle } from './lib/appVersion'
import { SITE_NAME } from './lib/brand'
import { headerAuthSlot, headerExtraLinks } from './lib/headerAuth'
import BrandMark from './components/BrandMark.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const mood = computed(() => {
  if (route.path.startsWith('/login') || route.path.startsWith('/register')) return 'mood-auth'
  if (route.path.startsWith('/referee') || route.path.startsWith('/analyst')) return 'mood-ref'
  if (route.path.startsWith('/admin')) return 'mood-admin'
  if (route.path.startsWith('/matches')) return 'mood-pitch'
  return 'mood-home'
})

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)
const compactQuery = '(max-width: 959px)'
const compact = ref(typeof window !== 'undefined' && window.matchMedia(compactQuery).matches)
let compactMedia: MediaQueryList | null = null

const commonLinks = [
  { to: '/', label: 'Главная' },
  { to: '/table', label: 'Таблица' },
  { to: '/calendar', label: 'Игры' },
  { to: '/live', label: 'Live' },
  { to: '/tournaments', label: 'Турниры' },
  { to: '/players', label: 'Игроки' },
]

const extraLinks = computed(() => headerExtraLinks((role) => auth.hasRole(role)))
const authSlot = computed(() => headerAuthSlot(auth.sessionReady, auth.isAuthenticated))

function syncCompact() {
  compact.value = !!compactMedia?.matches
}

function onPointerDown(event: PointerEvent) {
  if (!menuOpen.value || !menuRoot.value) return
  const target = event.target
  if (target instanceof Node && menuRoot.value.contains(target)) return
  menuOpen.value = false
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  compactMedia = window.matchMedia(compactQuery)
  syncCompact()
  compactMedia.addEventListener('change', syncCompact)
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKey)
})

onUnmounted(() => {
  compactMedia?.removeEventListener('change', syncCompact)
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKey)
})

watch(mood, (value) => {
  document.body.classList.remove('mood-auth', 'mood-pitch', 'mood-ref', 'mood-admin', 'mood-home')
  document.body.classList.add(value)
}, { immediate: true })

async function logout() {
  menuOpen.value = false
  await auth.logout()
  router.push('/')
}

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

watch(compact, () => {
  menuOpen.value = false
})

</script>

<template>
  <div class="shell" :data-mood="mood">
    <header class="chrome">
      <div class="nav-bar">
        <div class="container nav-inner">
          <RouterLink class="brand" to="/" :title="SITE_NAME">
            <BrandMark :size="36" />
            <span class="brand-name">{{ SITE_NAME }}</span>
          </RouterLink>

          <nav class="primary" aria-label="Разделы">
            <RouterLink
              v-for="item in commonLinks"
              :key="item.to"
              :to="item.to"
              :exact-active-class="item.to === '/' ? 'on' : undefined"
              :active-class="item.to === '/' ? undefined : 'on'"
            >{{ item.label }}</RouterLink>
          </nav>

          <div class="auth" :aria-busy="authSlot === 'pending'">
            <template v-if="authSlot === 'account'">
              <RouterLink class="avatar-link" to="/profile" aria-label="Профиль">
                <PlayerAvatar :src="auth.user?.photoUrl" :name="[auth.user?.lastName, auth.user?.firstName].filter(Boolean).join(' ') || auth.user?.email" :size="32" />
              </RouterLink>
              <button class="login-pill ghost" type="button" @click="logout">Выйти</button>
            </template>
            <RouterLink v-else-if="authSlot === 'login'" class="login-pill" to="/login">Войти</RouterLink>
            <span v-else class="auth-pending" aria-hidden="true"></span>
          </div>

          <div ref="menuRoot" class="menu" :class="{ 'has-extra': extraLinks.length }">
            <button
              class="menu-button"
              type="button"
              aria-label="Меню"
              aria-haspopup="true"
              :aria-expanded="menuOpen"
              @click="menuOpen = !menuOpen"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </button>
            <div v-if="menuOpen" class="menu-panel">
              <div class="only-compact">
                <RouterLink
                  v-for="item in commonLinks"
                  :key="`menu-${item.to}`"
                  :to="item.to"
                  :exact-active-class="item.to === '/' ? 'on' : undefined"
                  :active-class="item.to === '/' ? undefined : 'on'"
                >{{ item.label }}</RouterLink>
                <RouterLink v-if="authSlot === 'account'" class="menu-user" to="/profile">
                  <PlayerAvatar :src="auth.user?.photoUrl" :name="[auth.user?.lastName, auth.user?.firstName].filter(Boolean).join(' ') || auth.user?.email" :size="28" />
                  <span>Профиль</span>
                </RouterLink>
                <RouterLink v-else-if="authSlot === 'login'" to="/login">Войти</RouterLink>
              </div>
              <RouterLink
                v-for="item in extraLinks"
                :key="item.to"
                :to="item.to"
                active-class="on"
              >{{ item.label }}</RouterLink>
              <button v-if="authSlot === 'account'" class="menu-logout only-compact" type="button" @click="logout">Выйти</button>
            </div>
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
      <div class="container foot-inner">
        <span class="foot-ver" :title="appVersionTitle">{{ appVersionLabel }}</span>
      </div>
    </footer>

  </div>
</template>

<style scoped>
.shell { min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; }
.chrome { background: var(--navy); color: #fff; }
.nav-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  min-height: 68px;
  flex-wrap: nowrap;
}
.brand {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 0.65rem;
  color: #fff;
  text-decoration: none;
  font-family: var(--font-display);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.brand:hover { color: #fff; text-decoration: none; }
.brand-name {
  font-size: 0.92rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  line-height: 1;
  white-space: nowrap;
}
.brand :deep(.brand-mark) {
  width: 36px;
  height: 36px;
}
.primary {
  display: flex;
  gap: 0.15rem;
  flex: 1 1 auto;
  flex-wrap: nowrap;
  min-width: 0;
  justify-content: flex-start;
}
.primary a {
  color: rgba(255,255,255,0.82);
  font-weight: 700;
  font-size: 0.88rem;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  text-decoration: none;
  white-space: nowrap;
}
.primary a:hover { color: #fff; background: rgba(255,255,255,0.08); }
.primary a.on { color: var(--navy); background: var(--ice); }
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
.auth { display: flex; gap: 0.4rem; align-items: center; flex: 0 0 auto; }
.auth-pending { width: 4.8rem; height: 2rem; flex: 0 0 auto; }
.avatar-link { display: grid; place-items: center; }
.menu { position: relative; flex: 0 0 auto; display: none; }
.menu.has-extra { display: block; }
.only-compact { display: contents; }
.menu-button {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255,255,255,0.35);
  border-radius: 12px;
  background: transparent;
  color: #fff;
  cursor: pointer;
  padding: 0;
}
.menu-button:hover { background: rgba(255,255,255,0.08); }
.menu-button svg { width: 22px; height: 22px; }
.menu-panel {
  position: absolute;
  top: calc(100% + 0.45rem);
  right: 0;
  z-index: 50;
  display: grid;
  min-width: 220px;
  max-width: min(280px, calc(100vw - 1.2rem));
  padding: 0.35rem;
  background: #fff;
  color: var(--navy);
  border-radius: 14px;
  box-shadow: var(--shadow);
}
.menu-panel a,
.menu-logout {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--navy);
  text-align: left;
  text-decoration: none;
  font-weight: 700;
  font-size: 0.92rem;
  padding: 0.55rem 0.7rem;
  border-radius: 10px;
  cursor: pointer;
  white-space: nowrap;
}
.menu-panel a:hover,
.menu-logout:hover { background: rgba(76, 180, 229, 0.12); color: var(--navy); }
.menu-panel a.on { background: var(--ice); }
.main { padding: 1.4rem 0 2.4rem; }
.main.flush { padding: 0 0 2.4rem; }
.foot { color: var(--muted); font-size: 0.78rem; padding: 0 0 1.4rem; }
.foot-inner {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 1rem;
  flex-wrap: wrap;
}
.foot-ver {
  font-size: 0.75rem;
  color: #3a4c63;
  letter-spacing: 0.02em;
  white-space: nowrap;
}
@media (min-width: 960px) {
  .only-compact { display: none; }
}

@media (max-width: 1099px) {
  .nav-inner { min-height: 56px; }
  .primary a { padding: 0.35rem 0.58rem; font-size: 0.8rem; }
}

@media (max-width: 959px) {
  .primary, .auth { display: none; }
  .menu { display: block; }
  .nav-inner { min-height: 52px; }
  .brand-name { font-size: 0.82rem; }
}
</style>
