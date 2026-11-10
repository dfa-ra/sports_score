<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import PlayerAvatar from './components/PlayerAvatar.vue'
import { appVersionLabel, appVersionTitle } from './lib/appVersion'
import { SITE_NAME } from './lib/brand'
import { headerAccountLinks, headerAuthSlot } from './lib/headerAuth'
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
const accountOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)
const accountRoot = ref<HTMLElement | null>(null)
const compactQuery = '(max-width: 1023px)'
const compact = ref(typeof window !== 'undefined' && window.matchMedia(compactQuery).matches)
let compactMedia: MediaQueryList | null = null

const commonLinks = [
  { to: '/', label: 'Главная' },
  { to: '/table', label: 'Таблица' },
  { to: '/calendar', label: 'Игры' },
  { to: '/live', label: 'Live' },
  { to: '/tournaments', label: 'Турниры' },
  { to: '/teams', label: 'Команды' },
  { to: '/players', label: 'Игроки' },
]

const accountLinks = computed(() => headerAccountLinks((role) => auth.hasRole(role)))
const authSlot = computed(() => headerAuthSlot(auth.sessionReady, auth.isAuthenticated))

function syncCompact() {
  compact.value = !!compactMedia?.matches
}

function navActive(to: string) {
  const path = route.path
  if (to === '/') return path === '/'
  return path === to || path.startsWith(`${to}/`)
}

function onPointerDown(event: PointerEvent) {
  const target = event.target
  if (!(target instanceof Node)) return
  if (menuOpen.value && menuRoot.value && !menuRoot.value.contains(target)) menuOpen.value = false
  if (accountOpen.value && accountRoot.value && !accountRoot.value.contains(target)) accountOpen.value = false
}

function onKey(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  menuOpen.value = false
  accountOpen.value = false
}

function closeMenus() {
  menuOpen.value = false
  accountOpen.value = false
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
  closeMenus()
  await auth.logout()
  router.push('/')
}

watch(() => route.fullPath, () => {
  closeMenus()
})

watch(compact, () => {
  closeMenus()
})

const pageFrame = computed(() => {
  if (route.path === '/') return 'home-bleed'
  if (route.path === '/referee' || route.path.startsWith('/referee/')) return 'referee-frame rise'
  return 'container rise'
})

const q2Chrome = computed(() => {
  const name = String(route.name || '')
  return name === 'tournaments' || name === 'players' || name === 'login'
})

const stickyChrome = computed(() => {
  if (q2Chrome.value) return true
  const name = String(route.name || '')
  if (name === 'calendar' || name === 'live' || name === 'match') return true
  if (name === 'table') return String(route.query.tab || 'table') !== 'playoff'
  if (name === 'tournament') return true
  return false
})

</script>

<template>
  <div class="shell" :data-mood="mood">
    <header class="chrome" :class="{ pin: stickyChrome, 'q2-pin': q2Chrome }">
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
              :class="{ on: navActive(item.to) }"
            >{{ item.label }}</RouterLink>
          </nav>

          <div class="session">
          <div class="auth" :aria-busy="authSlot === 'pending'">
            <div v-if="authSlot === 'account'" ref="accountRoot" class="account">
              <button
                class="profile-button"
                type="button"
                aria-label="Профиль"
                aria-haspopup="true"
                :aria-expanded="accountOpen"
                @click="accountOpen = !accountOpen"
              >
                <PlayerAvatar :src="auth.user?.photoUrl" :name="[auth.user?.lastName, auth.user?.firstName].filter(Boolean).join(' ') || auth.user?.email" :first-name="auth.user?.firstName" :last-name="auth.user?.lastName" :size="32" />
              </button>
              <div v-if="accountOpen" class="menu-panel">
                <RouterLink
                  v-for="item in accountLinks"
                  :key="`account-${item.to}`"
                  :to="item.to"
                  :class="{ on: navActive(item.to) }"
                >{{ item.label }}</RouterLink>
                <button class="menu-logout" type="button" @click="logout">Выйти</button>
              </div>
            </div>
            <RouterLink v-else-if="authSlot === 'login'" class="login-pill" to="/login">Войти</RouterLink>
            <span v-else class="auth-pending" aria-hidden="true"></span>
          </div>

          <span class="session-rule" aria-hidden="true"></span>

          <div ref="menuRoot" class="menu">
            <button
              class="menu-button"
              type="button"
              :aria-label="menuOpen ? 'Закрыть меню' : 'Открыть меню'"
              aria-haspopup="true"
              :aria-expanded="menuOpen"
              @click="menuOpen = !menuOpen"
            >
              <span class="menu-icon" :class="{ open: menuOpen }" aria-hidden="true">
                <span></span>
                <span></span>
                <span v-if="!menuOpen"></span>
              </span>
            </button>
            <div v-if="menuOpen" class="menu-panel">
              <RouterLink
                v-for="item in commonLinks"
                :key="`menu-${item.to}`"
                :to="item.to"
                :class="{ on: navActive(item.to) }"
              >{{ item.label }}</RouterLink>
              <template v-if="authSlot === 'account'">
                <div class="menu-sep" role="separator"></div>
                <RouterLink
                  v-for="item in accountLinks"
                  :key="`burger-${item.to}`"
                  :to="item.to"
                  :class="{ on: navActive(item.to) }"
                >{{ item.label }}</RouterLink>
                <button class="menu-logout" type="button" @click="logout">Выйти</button>
              </template>
            </div>
          </div>
          </div>
        </div>
      </div>
    </header>
    <main class="main" :class="{ flush: route.path === '/' }">
      <div :class="pageFrame">
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
.shell { min-height: 100vh; min-width: 0; display: grid; grid-template-rows: auto 1fr auto; }
.shell > * { min-width: 0; }
.chrome { background: var(--navy); color: #fff; }
.chrome.pin {
  position: sticky;
  top: 0;
  z-index: 30;
}
@media (min-width: 721px) {
  .chrome.q2-pin .nav-inner { min-height: 64px; }
}
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
.session {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  margin-left: auto;
}
.session-rule { display: none; }
.login-pill {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 40px;
  border: 1px solid rgba(255,255,255,0.45);
  color: #fff;
  border-radius: 10px;
  padding: 0 16px;
  text-decoration: none;
  background: transparent;
  font-family: var(--font-display);
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.login-pill:hover,
.login-pill:active { background: rgba(255,255,255,0.08); color: #fff; }
.login-pill.ghost { border-color: transparent; color: rgba(255,255,255,0.75); }
.auth-pending { box-sizing: border-box; width: 40px; height: 40px; flex: 0 0 auto; }
.auth { display: flex; align-items: center; flex: 0 0 auto; }
.login-pill:focus-visible,
.menu-button:focus-visible,
.profile-button:focus-visible {
  outline: 2px solid #4cb4e5;
  outline-offset: 0;
}
.account { position: relative; display: none; }
.profile-button {
  position: relative;
  box-sizing: border-box;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255,255,255,.35);
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
  padding: 0;
  overflow: hidden;
}
.profile-button:hover { background: rgba(255,255,255,.14); border-radius: 50%; }
.profile-button :deep(.player-avatar) {
  width: 100% !important;
  height: 100% !important;
}
.menu { position: relative; flex: 0 0 auto; display: none; }
.menu-sep {
  height: 1px;
  margin: 0.3rem 0.45rem;
  background: rgba(255, 255, 255, 0.22);
}
.menu-button {
  position: relative;
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
.menu-button:hover,
.menu-button:active { background: rgba(255,255,255,0.08); }
.menu-icon {
  display: grid;
  width: 18px;
  gap: 4px;
}
.menu-icon span {
  display: block;
  width: 18px;
  height: 2px;
  border-radius: 1px;
  background: currentColor;
}
.menu-icon.open {
  position: relative;
  display: block;
  width: 14px;
  height: 14px;
}
.menu-icon.open span {
  position: absolute;
  left: 0;
  top: 6px;
  width: 14px;
  height: 2px;
}
.menu-icon.open span:first-child { transform: rotate(45deg); }
.menu-icon.open span:last-child { transform: rotate(-45deg); }
.menu-panel {
  position: absolute;
  top: calc(100% + 0.45rem);
  right: 0;
  z-index: 50;
  display: grid;
  min-width: 220px;
  max-width: min(280px, calc(100vw - 1.2rem));
  padding: 0.35rem;
  background: #00205b;
  color: #fff;
  border-radius: 14px;
  box-shadow: 0 16px 28px rgba(0, 16, 46, 0.35);
}
.menu-panel a,
.menu-logout {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  border: 0;
  background: transparent;
  color: #fff;
  text-align: left;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.92rem;
  padding: 0.55rem 0.7rem;
  border-radius: 10px;
  cursor: pointer;
  white-space: nowrap;
}
.menu-panel a:hover,
.menu-logout:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.menu-panel a.on {
  background: transparent;
  color: #fff;
  font-weight: 800;
  box-shadow: inset 4px 0 0 #4cb4e5;
}
.referee-frame {
  width: min(1260px, calc(100% - 48px));
  margin: 0 auto;
  min-width: 0;
}
@media (min-width: 1440px) {
  .referee-frame { width: min(1260px, calc(100% - 180px)); }
}
@media (max-width: 719px) {
  .referee-frame { width: min(1260px, calc(100% - 28px)); }
}
.main { padding: var(--main-pad-top) 0 var(--main-pad-bottom); }
.main.flush { padding: 0 0 var(--main-pad-bottom); }
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
  max-width: 100%;
}
@media (min-width: 1024px) {
  .menu { display: none; }
  .account { display: block; }
}

@media (max-width: 1099px) {
  .nav-inner { min-height: 56px; }
  .primary a { padding: 0.35rem 0.58rem; font-size: 0.8rem; }
}

@media (max-width: 1023px) {
  .primary { display: none; }
  .menu { display: block; }
  .account { display: block; }
  .nav-inner { min-height: 52px; }
  .brand { min-width: 0; }
  .brand-name { font-size: 0.82rem; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .session {
    align-items: stretch;
    box-sizing: border-box;
    height: 40px;
    border: 1px solid rgba(255,255,255,0.45);
    border-radius: 10px;
    background: transparent;
  }
  .session-rule {
    display: block;
    align-self: center;
    flex: 0 0 1px;
    width: 1px;
    height: 24px;
    background: rgba(255,255,255,0.3);
  }
  .session .auth,
  .session .account,
  .session .menu { height: 100%; }
  .session .auth { align-items: stretch; }
  .session .login-pill {
    flex: 0 0 auto;
    height: 100%;
    min-width: 84px;
    padding: 0 16px;
    border: 0;
    border-radius: 9px 0 0 9px;
    font-family: var(--font-display);
    font-size: 14px;
    font-weight: 700;
    color: #fff;
  }
  .session .login-pill:hover,
  .session .login-pill:active { border-radius: 9px 0 0 9px; }
  .session .menu,
  .session .menu-button { flex-shrink: 0; margin-left: 0; }
  .session .menu { display: flex; }
  .session .menu-button {
    width: 44px;
    height: 100%;
    border: 0;
    border-radius: 0 9px 9px 0;
  }
  .session .menu-button:hover,
  .session .menu-button:active { border-radius: 0 9px 9px 0; }
  .session .profile-button {
    width: 48px;
    height: 100%;
    overflow: visible;
    border: 0;
    border-radius: 9px 0 0 9px;
  }
  .session .profile-button:hover,
  .session .profile-button:active {
    background: rgba(255,255,255,0.08);
    border-radius: 9px 0 0 9px;
  }
  .session .profile-button :deep(.player-avatar) {
    width: 28px !important;
    height: 28px !important;
  }
  .session .auth-pending { width: 48px; height: 100%; }
  .session .login-pill::before,
  .session .menu-button::before,
  .session .profile-button::before {
    content: "";
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: 44px;
    transform: translateY(-50%);
  }
}
@media (max-width: 720px) {
  .nav-inner { justify-content: flex-start; min-height: var(--chrome-h); gap: 8px; }
  .brand { gap: 8px; }
  .brand-name { font-size: 13px; font-weight: 700; line-height: 16px; letter-spacing: 0.04em; }
  .brand :deep(.brand-mark) { width: 34px !important; height: 34px !important; }
  .foot-inner { min-width: 0; padding-right: 0.15rem; }
  .foot-ver {
    min-width: 0;
    white-space: normal;
    text-align: right;
    overflow-wrap: anywhere;
  }
}
</style>
