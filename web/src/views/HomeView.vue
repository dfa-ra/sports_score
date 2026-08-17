<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const liveMatches = ref<any[]>([])
const upcoming = ref<any[]>([])
const loaded = ref(false)

onMounted(async () => {
  if (!auth.isAuthenticated) {
    loaded.value = true
    return
  }
  try {
    const { data } = await api.get('/matches', { params: { size: 12, sort: 'scheduledAt,desc' } })
    const items = data.content ?? []
    liveMatches.value = items.filter((m: any) => m.status === 'LIVE' || m.status === 'PAUSED')
    upcoming.value = items.filter((m: any) => m.status === 'SCHEDULED').slice(0, 4)
  } catch {
    // guest / offline
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
        <h1>Student League</h1>
        <p class="lede">
          Live-счёт, турниры и статистика по нескольким видам спорта —
          плюс кабинет капитана, судьи и администратора.
        </p>
        <div class="cta">
          <RouterLink class="btn" to="/matches">Смотреть матчи</RouterLink>
          <RouterLink class="btn secondary" to="/tournaments">Турниры</RouterLink>
          <RouterLink v-if="!auth.isAuthenticated" class="btn success" to="/register">Создать аккаунт</RouterLink>
        </div>
      </div>
      <div class="hero-side">
        <div class="stat">
          <span class="stat-label">Live сейчас</span>
          <strong>{{ liveMatches.length }}</strong>
        </div>
        <div class="stat">
          <span class="stat-label">Скоро</span>
          <strong>{{ upcoming.length }}</strong>
        </div>
        <div class="stat accent">
          <span class="stat-label">Режим</span>
          <strong>{{ auth.role ?? 'FAN' }}</strong>
        </div>
      </div>
    </div>

    <div v-if="auth.isAuthenticated" class="grid cards">
      <RouterLink class="panel feature" to="/matches">
        <h2>Матчи</h2>
        <p>Расписание, live-счёт и лента событий без polling.</p>
      </RouterLink>
      <RouterLink class="panel feature" to="/teams">
        <h2>Команды</h2>
        <p>Составы, капитаны и заявки на турниры.</p>
      </RouterLink>
      <RouterLink class="panel feature" to="/statistics">
        <h2>Статистика</h2>
        <p>Голы, ассисты и таблица — из MatchEvent.</p>
      </RouterLink>
      <RouterLink
        v-if="auth.role === 'REFEREE' || auth.role === 'ADMIN'"
        class="panel feature"
        to="/referee"
      >
        <h2>Кабинет судьи</h2>
        <p>Крупные кнопки для быстрого ввода событий.</p>
      </RouterLink>
    </div>

    <div v-if="loaded && auth.isAuthenticated && liveMatches.length" class="stack" style="margin-top:1rem">
      <div class="page-title">
        <h2>Идут сейчас</h2>
      </div>
      <div class="grid cards">
        <RouterLink
          v-for="m in liveMatches"
          :key="m.id"
          class="panel live-pulse"
          :to="`/matches/${m.id}`"
        >
          <span class="badge live">{{ m.status }}</span>
          <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
          <p class="muted">Открыть live-трансляцию событий</p>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home { display: grid; gap: 1.25rem; }
.hero {
  display: grid;
  grid-template-columns: 1.6fr 0.9fr;
  gap: 1.5rem;
  padding: 1.75rem;
  background:
    linear-gradient(135deg, rgba(88, 166, 255, 0.08), transparent 42%),
    var(--surface);
}
.eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.72rem;
  color: var(--accent);
  margin: 0 0 0.6rem;
  font-weight: 700;
}
h1 {
  font-size: clamp(2.2rem, 5vw, 3.2rem);
  line-height: 1.05;
  max-width: 12ch;
}
.lede { max-width: 36rem; margin-top: 0.75rem; font-size: 1.02rem; }
.cta { display: flex; gap: 0.65rem; flex-wrap: wrap; margin-top: 1.2rem; }
.hero-side { display: grid; gap: 0.75rem; align-content: center; }
.stat {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.9rem 1rem;
  background: var(--bg);
}
.stat.accent { border-color: rgba(88, 166, 255, 0.35); background: var(--accent-soft); }
.stat-label { display: block; color: var(--muted); font-size: 0.78rem; margin-bottom: 0.2rem; }
.stat strong { font-size: 1.35rem; color: var(--text-strong); }
.feature { text-decoration: none; color: inherit; transition: transform 0.15s ease; }
.feature:hover { transform: translateY(-2px); text-decoration: none; border-color: rgba(88, 166, 255, 0.4); }
.feature h2 { font-size: 1.15rem; margin-bottom: 0.35rem; }
@media (max-width: 860px) {
  .hero { grid-template-columns: 1fr; }
}
</style>
