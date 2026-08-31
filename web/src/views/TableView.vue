<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import StandingTable from '../components/StandingTable.vue'
import { useTeamDirectory } from '../lib/useTeamDirectory'

type Tab = 'table' | 'results' | 'scorers' | 'players'

const route = useRoute()
const router = useRouter()
const teams = useTeamDirectory()
const tournaments = ref<any[]>([])
const tournamentId = ref('')
const standings = ref<any[]>([])
const matches = ref<any[]>([])
const scorers = ref<any[]>([])
const assists = ref<any[]>([])
const keepers = ref<any[]>([])
const players = ref<any[]>([])
const tournament = ref<any>(null)
const loading = ref(true)

const tab = computed<Tab>(() => {
  const value = String(route.query.tab || 'table')
  if (value === 'results' || value === 'scorers' || value === 'players') return value
  return 'table'
})

const season = computed(() => {
  const start = tournament.value?.startsOn || tournament.value?.startDate
  if (!start) return ''
  const year = new Date(start).getFullYear()
  return `${year}/${year + 1}`
})

const results = computed(() =>
  matches.value
    .filter((m) => m.tournamentId === tournamentId.value && (m.status === 'FINISHED' || m.status === 'CANCELLED'))
    .slice()
    .sort((a, b) => String(b.scheduledAt).localeCompare(String(a.scheduledAt)))
)

onMounted(async () => {
  await teams.load()
  const [{ data }, games] = await Promise.all([
    api.get('/tournaments', { params: { size: 50 } }),
    api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } }),
  ])
  tournaments.value = data.content ?? []
  matches.value = games.data.content ?? []
  try {
    const current = await api.get('/tournaments/current')
    if (current.data?.id) tournamentId.value = current.data.id
  } catch {
    if (tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  }
  if (!tournamentId.value && tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  try {
    const list = await api.get('/players', { params: { size: 100 } })
    players.value = list.data.content ?? []
  } catch {
    players.value = []
  }
  await load()
})

watch(tournamentId, load)

function setTab(next: Tab) {
  router.replace({ query: next === 'table' ? {} : { tab: next } })
}

async function load() {
  if (!tournamentId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [t, s, g, a, k] = await Promise.all([
      api.get(`/tournaments/${tournamentId.value}`),
      api.get(`/tournaments/${tournamentId.value}/standings`),
      api.get('/statistics/scorers', { params: { tournamentId: tournamentId.value, limit: 30 } }),
      api.get('/statistics/assists', { params: { tournamentId: tournamentId.value, limit: 30 } }),
      api.get('/statistics/goalkeepers', { params: { tournamentId: tournamentId.value, limit: 30 } }),
    ])
    tournament.value = t.data
    standings.value = s.data
    scorers.value = g.data
    assists.value = a.data
    keepers.value = k.data
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="stack page">
    <div class="league">
      <span class="mark">KB</span>
      <div>
        <p class="where">KRONBARS</p>
        <h1>{{ tournament?.name || 'Таблица' }}</h1>
        <label class="field season">
          <select v-model="tournamentId">
            <option v-for="t in tournaments" :key="t.id" :value="t.id">
              {{ t.name }}{{ season && t.id === tournamentId ? ' · ' + season : '' }}
            </option>
          </select>
        </label>
      </div>
    </div>

    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'table' }" @click="setTab('table')">Таблица</button>
      <button type="button" :class="{ on: tab === 'results' }" @click="setTab('results')">Результаты</button>
      <button type="button" :class="{ on: tab === 'scorers' }" @click="setTab('scorers')">Бомбардиры</button>
      <button type="button" :class="{ on: tab === 'players' }" @click="setTab('players')">Игроки</button>
    </div>

    <div v-if="loading" class="skeleton" />

    <template v-else-if="tab === 'table'">
      <EmptyState v-if="!standings.length" title="Таблица пустая" text="Нет утверждённых команд или сыгранных матчей." />
      <div v-else class="sheet">
        <StandingTable :rows="standings" />
      </div>
    </template>

    <template v-else-if="tab === 'results'">
      <EmptyState v-if="!results.length" title="Сыгранных матчей ещё нет" />
      <div v-else class="sheet">
        <MatchRow
          v-for="m in results"
          :key="m.id"
          :match="m"
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
      </div>
    </template>

    <template v-else-if="tab === 'players'">
      <EmptyState v-if="!players.length" title="Игроков пока нет" />
      <div v-else class="sheet people">
        <RouterLink v-for="p in players" :key="p.id" class="person" :to="`/players/${p.id}`">
          <PlayerAvatar :src="p.avatarUrl" :name="p.displayName || `${p.firstName} ${p.lastName}`" :size="36" />
          <div>
            <b>{{ p.displayName || `${p.firstName} ${p.lastName}` }}</b>
            <p>{{ p.position || 'Игрок' }} · №{{ p.jerseyNumber ?? '—' }}</p>
          </div>
        </RouterLink>
      </div>
    </template>

    <template v-else>
      <div class="panel">
        <h2>Голы</h2>
        <EmptyState v-if="!scorers.length" title="Голов ещё нет" />
        <div v-else class="table-wrap">
          <table class="table">
            <thead><tr><th>Игрок</th><th>Голы</th><th>Игры</th></tr></thead>
            <tbody>
              <tr v-for="p in scorers" :key="p.playerId">
                <td><RouterLink :to="`/players/${p.playerId}`">{{ p.displayName }}</RouterLink></td>
                <td>{{ p.goals }}</td>
                <td>{{ p.appearances }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="panel">
        <h2>Передачи</h2>
        <EmptyState v-if="!assists.length" title="Передач ещё нет" />
        <div v-else class="table-wrap">
          <table class="table">
            <thead><tr><th>Игрок</th><th>Пасы</th><th>Игры</th></tr></thead>
            <tbody>
              <tr v-for="p in assists" :key="p.playerId">
                <td><RouterLink :to="`/players/${p.playerId}`">{{ p.displayName }}</RouterLink></td>
                <td>{{ p.assists }}</td>
                <td>{{ p.appearances }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="panel">
        <h2>Сухие</h2>
        <EmptyState v-if="!keepers.length" title="Сухих матчей ещё нет" />
        <div v-else class="table-wrap">
          <table class="table">
            <thead><tr><th>Игрок</th><th>Сухие</th><th>Игры</th></tr></thead>
            <tbody>
              <tr v-for="p in keepers" :key="p.playerId">
                <td><RouterLink :to="`/players/${p.playerId}`">{{ p.displayName }}</RouterLink></td>
                <td>{{ p.cleanSheets }}</td>
                <td>{{ p.appearances }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.page { gap: 0.45rem; }
.league {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 0.75rem;
  align-items: center;
  padding: 0.35rem 0 0.2rem;
}
.mark {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #fff;
  color: var(--navy);
  font-weight: 800;
  box-shadow: var(--shadow);
}
.where {
  margin: 0;
  color: var(--muted);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
}
.league h1 { font-size: clamp(1.2rem, 3vw, 1.7rem); }
.season { max-width: 360px; margin-top: 0.35rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
h2 { font-size: 1.05rem; margin: 0 0 0.65rem; }
.people { display: grid; }
.person {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.7rem;
  align-items: center;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--line);
  color: inherit;
  text-decoration: none;
}
.person b { color: var(--navy); }
.person p { margin: 0.1rem 0 0; color: var(--muted); font-size: 0.82rem; }
</style>
