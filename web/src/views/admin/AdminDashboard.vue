<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import { labelOf, roleLabel } from '../../lib/format'
import StatusBadge from '../../components/StatusBadge.vue'

const users = ref<any[]>([])
const tournaments = ref<any[]>([])
const matches = ref<any[]>([])
const teams = ref<any[]>([])
const players = ref<any[]>([])
const sports = ref<any[]>([])
const tab = ref<'users' | 'tournaments' | 'matches' | 'teams' | 'players' | 'referees' | 'statistics'>('users')
const teamStats = ref<any[]>([])
const playerStats = ref<any[]>([])

const tabs = [
  { id: 'users', label: 'Пользователи' },
  { id: 'tournaments', label: 'Турниры' },
  { id: 'matches', label: 'Матчи' },
  { id: 'teams', label: 'Команды' },
  { id: 'players', label: 'Игроки' },
  { id: 'referees', label: 'Судьи' },
  { id: 'statistics', label: 'Статистика' },
] as const

onMounted(async () => {
  const [u, t, m, tm, p, s, ts, ps] = await Promise.all([
    api.get('/admin/users', { params: { size: 50 } }),
    api.get('/tournaments', { params: { size: 50 } }),
    api.get('/matches', { params: { size: 50 } }),
    api.get('/teams', { params: { size: 50 } }),
    api.get('/players', { params: { size: 50 } }),
    api.get('/sports'),
    api.get('/statistics/teams'),
    api.get('/statistics/players'),
  ])
  users.value = u.data.content
  tournaments.value = t.data.content
  matches.value = m.data.content
  teams.value = tm.data.content
  players.value = p.data.content
  sports.value = s.data
  teamStats.value = ts.data
  playerStats.value = ps.data
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Служебный вход</p>
      <h1>Админ-панель</h1>
      <p>Пользователи, турниры, матчи и статистика. Без Swagger — всё, что нужно, уже здесь.</p>
    </div>
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="btn secondary"
        :class="{ on: tab === t.id }"
        @click="tab = t.id"
      >{{ t.label }}</button>
    </div>

    <div v-if="tab === 'users'" class="panel">
      <h2>Пользователи</h2>
      <table class="table">
        <thead><tr><th>Email</th><th>Роль</th><th>Активен</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.email }}</td>
            <td>{{ labelOf(roleLabel, u.role) }}</td>
            <td>{{ u.enabled ? 'да' : 'нет' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="tab === 'tournaments'" class="panel">
      <h2>Турниры</h2>
      <div v-for="t in tournaments" :key="t.id" class="row">
        <RouterLink :to="`/tournaments/${t.id}`">{{ t.name }}</RouterLink>
        <StatusBadge :status="t.status" />
      </div>
    </div>

    <div v-else-if="tab === 'matches'" class="panel">
      <h2>Матчи</h2>
      <div v-for="m in matches" :key="m.id" class="row">
        <RouterLink :to="`/matches/${m.id}`">{{ m.homeScore }}:{{ m.awayScore }}</RouterLink>
        <StatusBadge :status="m.status" />
      </div>
    </div>

    <div v-else-if="tab === 'teams'" class="panel">
      <h2>Команды</h2>
      <div v-for="t in teams" :key="t.id" class="row">
        <RouterLink :to="`/teams/${t.id}`">{{ t.name }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'players'" class="panel">
      <h2>Игроки</h2>
      <div v-for="p in players" :key="p.id" class="row">
        <RouterLink :to="`/players/${p.id}`">{{ p.displayName || `${p.firstName} ${p.lastName}` }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'referees'" class="panel">
      <h2>Судьи</h2>
      <div v-for="u in users.filter(x => x.role === 'REFEREE')" :key="u.id" class="row">{{ u.email }}</div>
      <p class="muted">Виды спорта: {{ sports.map(s => s.code).join(', ') || 'пока не заданы' }}</p>
    </div>

    <div v-else class="panel stack">
      <h2>Статистика</h2>
      <p>Топ игроков по голам:</p>
      <div v-for="p in playerStats.slice(0, 10)" :key="p.playerId" class="row">
        {{ p.displayName }} · G{{ p.goals }} A{{ p.assists }}
      </div>
      <p>Команды:</p>
      <div v-for="t in teamStats.slice(0, 10)" :key="t.teamId" class="row">
        {{ t.teamName }} · {{ t.points }} очков
      </div>
    </div>
  </section>
</template>

<style scoped>
.tabs { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: #22180b; border-color: transparent; }
h2 { font-size: 1.15rem; margin-bottom: 0.55rem; }
.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--line);
}
</style>
