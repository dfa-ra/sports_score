<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'

const users = ref<any[]>([])
const tournaments = ref<any[]>([])
const matches = ref<any[]>([])
const teams = ref<any[]>([])
const players = ref<any[]>([])
const sports = ref<any[]>([])
const tab = ref<'users' | 'tournaments' | 'matches' | 'teams' | 'players' | 'referees' | 'statistics'>('users')
const teamStats = ref<any[]>([])
const playerStats = ref<any[]>([])

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
    <h1>Admin Dashboard</h1>
    <p>Manage users, tournaments, matches, teams, players, referees, and statistics.</p>
    <div class="tabs">
      <button v-for="t in ['users','tournaments','matches','teams','players','referees','statistics']" :key="t"
              class="btn secondary" :class="{ active: tab === t }" @click="tab = t as any">{{ t }}</button>
    </div>

    <div v-if="tab === 'users'" class="panel">
      <h2>Users</h2>
      <table class="table">
        <thead><tr><th>Email</th><th>Role</th><th>Enabled</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id"><td>{{ u.email }}</td><td>{{ u.role }}</td><td>{{ u.enabled }}</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="tab === 'tournaments'" class="panel">
      <h2>Tournaments</h2>
      <div v-for="t in tournaments" :key="t.id">
        <RouterLink :to="`/tournaments/${t.id}`">{{ t.name }}</RouterLink> · {{ t.status }}
      </div>
    </div>

    <div v-else-if="tab === 'matches'" class="panel">
      <h2>Matches</h2>
      <div v-for="m in matches" :key="m.id">
        <RouterLink :to="`/matches/${m.id}`">{{ m.status }} {{ m.homeScore }}:{{ m.awayScore }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'teams'" class="panel">
      <h2>Teams</h2>
      <div v-for="t in teams" :key="t.id"><RouterLink :to="`/teams/${t.id}`">{{ t.name }}</RouterLink></div>
    </div>

    <div v-else-if="tab === 'players'" class="panel">
      <h2>Players</h2>
      <div v-for="p in players" :key="p.id">
        <RouterLink :to="`/players/${p.id}`">{{ p.displayName || `${p.firstName} ${p.lastName}` }}</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'referees'" class="panel">
      <h2>Referees</h2>
      <div v-for="u in users.filter(x => x.role === 'REFEREE')" :key="u.id">{{ u.email }}</div>
      <p class="muted">Sports catalog: {{ sports.map(s => s.code).join(', ') }}</p>
    </div>

    <div v-else class="panel">
      <h2>Statistics</h2>
      <p>Top players by goals:</p>
      <div v-for="p in playerStats.slice(0, 10)" :key="p.playerId">{{ p.displayName }} · G{{ p.goals }} A{{ p.assists }}</div>
      <p>Teams:</p>
      <div v-for="t in teamStats.slice(0, 10)" :key="t.teamId">{{ t.teamName }} · {{ t.points }} pts</div>
    </div>
  </section>
</template>

<style scoped>
.tabs { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.active { background: var(--accent); color: #102015; }
</style>
