<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { formatWhen, labelOf, roleLabel } from '../lib/format'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import StatusBadge from '../components/StatusBadge.vue'

const auth = useAuthStore()
const liveMatches = ref<any[]>([])
const upcoming = ref<any[]>([])
const loaded = ref(false)
const teams = useTeamDirectory()

onMounted(async () => {
  try {
    await teams.load()
    const { data } = await api.get('/matches', { params: { size: 12, sort: 'scheduledAt,desc' } })
    const items = data.content ?? []
    liveMatches.value = items.filter((m: any) => m.status === 'LIVE' || m.status === 'PAUSED')
    upcoming.value = items.filter((m: any) => m.status === 'SCHEDULED').slice(0, 4)
  } catch {
    // offline
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <section class="home">
    <div class="hero panel rise">
      <div class="hero-copy">
        <p class="eyebrow">Студенческая спортивная лига</p>
        <h1>Счёт живой. Трибуна тоже.</h1>
        <p class="lede">
          Смотрите матчи, таблицы и статистику без регистрации.
          Аккаунт нужен только капитанам, судьям и тем, кто хочет выйти на поле.
        </p>
        <div class="cta">
          <RouterLink class="btn" to="/matches">Смотреть матчи</RouterLink>
          <RouterLink class="btn secondary" to="/tournaments">Турниры</RouterLink>
          <RouterLink v-if="!auth.isAuthenticated" class="btn success" to="/register">Стать игроком</RouterLink>
        </div>
      </div>
      <div class="hero-side">
        <div class="stat">
          <span class="stat-label">Live сейчас</span>
          <strong>{{ liveMatches.length }}</strong>
        </div>
        <div class="stat tilt">
          <span class="stat-label">Скоро</span>
          <strong>{{ upcoming.length }}</strong>
        </div>
        <div class="stat accent">
          <span class="stat-label">Вы</span>
          <strong>{{ auth.isAuthenticated ? labelOf(roleLabel, auth.role) : 'Зритель' }}</strong>
        </div>
      </div>
    </div>

    <div class="grid cards">
      <RouterLink class="panel feature" to="/matches">
        <h2>Матчи</h2>
        <p>Расписание, live-счёт и лента событий. Без бесконечного обновления страницы.</p>
      </RouterLink>
      <RouterLink class="panel feature" to="/teams">
        <h2>Команды</h2>
        <p>Составы, капитаны и заявки. Как заявка в общагу, только спортивнее.</p>
      </RouterLink>
      <RouterLink class="panel feature" to="/statistics">
        <h2>Статистика</h2>
        <p>Голы, пасы и таблица — из реальных событий, не из легенд раздевалки.</p>
      </RouterLink>
      <RouterLink
        v-if="auth.canOfficiate"
        class="panel feature"
        to="/referee"
      >
        <h2>Кабинет судьи</h2>
        <p>Крупные кнопки. Пальцы не промахнутся даже в дождь.</p>
      </RouterLink>
    </div>

    <div v-if="loaded && liveMatches.length" class="stack">
      <div class="page-title">
        <h2>Идут сейчас</h2>
        <p>Пока свисток не прозвучал — можно дышать вместе со счётом.</p>
      </div>
      <div class="grid cards">
        <RouterLink
          v-for="m in liveMatches"
          :key="m.id"
          class="panel live-pulse"
          :to="`/matches/${m.id}`"
        >
          <StatusBadge :status="m.status" />
          <div class="versus">{{ teams.name(m.homeTeamId) }} — {{ teams.name(m.awayTeamId) }}</div>
          <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
          <p class="muted">Открыть live-ленту</p>
        </RouterLink>
      </div>
    </div>

    <div v-if="loaded && upcoming.length" class="stack">
      <div class="page-title">
        <h2>На подходе</h2>
      </div>
      <div class="grid cards">
        <RouterLink v-for="m in upcoming" :key="m.id" class="panel" :to="`/matches/${m.id}`">
          <StatusBadge :status="m.status" />
          <div class="versus">{{ teams.name(m.homeTeamId) }} — {{ teams.name(m.awayTeamId) }}</div>
          <p class="muted">{{ formatWhen(m.scheduledAt) }}</p>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home { display: grid; gap: 1.35rem; }
.hero {
  display: grid;
  grid-template-columns: 1.55fr 0.9fr;
  gap: 1.5rem;
  padding: 1.9rem;
  border-radius: 28px 20px 26px 18px;
}
.lede { max-width: 36rem; margin-top: 0.75rem; font-size: 1.05rem; }
.cta { display: flex; gap: 0.65rem; flex-wrap: wrap; margin-top: 1.25rem; }
.hero-side { display: grid; gap: 0.75rem; align-content: center; }
.stat {
  border: 1px solid var(--line);
  border-radius: 16px 13px 15px 12px;
  padding: 0.95rem 1rem;
  background: color-mix(in srgb, var(--navy) 55%, transparent);
}
.stat.tilt { transform: rotate(-0.6deg); }
.stat.accent { border-color: rgba(98, 181, 229, 0.4); background: var(--accent-soft); }
.stat-label { display: block; color: var(--muted); font-size: 0.76rem; margin-bottom: 0.2rem; }
.stat strong { font-size: 1.4rem; color: var(--text-strong); font-family: var(--font-display); }
.feature h2 { font-size: 1.25rem; margin-bottom: 0.35rem; }
.versus { font-weight: 750; color: var(--text-strong); margin-top: 0.45rem; }
@media (max-width: 860px) {
  .hero { grid-template-columns: 1fr; }
  .stat.tilt { transform: none; }
}
</style>
