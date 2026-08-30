<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'

const tournaments = ref<any[]>([])
const tournamentId = ref('')
const standings = ref<any[]>([])
const tournament = ref<any>(null)
const loading = ref(true)

onMounted(async () => {
  const { data } = await api.get('/tournaments', { params: { size: 50 } })
  tournaments.value = data.content ?? []
  try {
    const current = await api.get('/tournaments/current')
    if (current.data?.id) tournamentId.value = current.data.id
  } catch {
    if (tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  }
  if (!tournamentId.value && tournaments.value[0]) tournamentId.value = tournaments.value[0].id
  await loadTable()
})

watch(tournamentId, loadTable)

async function loadTable() {
  if (!tournamentId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [t, s] = await Promise.all([
      api.get(`/tournaments/${tournamentId.value}`),
      api.get(`/tournaments/${tournamentId.value}/standings`),
    ])
    tournament.value = t.data
    standings.value = s.data
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="head">
      <p class="eyebrow">Турнир</p>
      <h1>{{ tournament?.name || 'Таблица' }}</h1>
      <label class="field season">
        <select v-model="tournamentId">
          <option v-for="t in tournaments" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </label>
    </div>
    <div class="fs-tabs">
      <RouterLink class="on" to="/table">Таблица</RouterLink>
      <RouterLink to="/calendar?tab=played">Результаты</RouterLink>
      <RouterLink to="/statistics">Бомбардиры</RouterLink>
    </div>
    <div v-if="loading" class="skeleton" />
    <EmptyState v-else-if="!standings.length" title="Таблица пустая" text="Нет утверждённых команд или сыгранных матчей." />
    <div v-else class="sheet table-wrap">
      <table class="table dense">
        <thead>
          <tr>
            <th>#</th>
            <th>Команда</th>
            <th>И</th>
            <th class="wide">В</th>
            <th class="wide">Н</th>
            <th class="wide">П</th>
            <th>Г</th>
            <th>О</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in standings" :key="row.teamId">
            <td class="pos">{{ i + 1 }}</td>
            <td><RouterLink :to="`/teams/${row.teamId}`">{{ row.teamName }}</RouterLink></td>
            <td>{{ row.played }}</td>
            <td class="wide">{{ row.wins }}</td>
            <td class="wide">{{ row.draws }}</td>
            <td class="wide">{{ row.losses }}</td>
            <td>{{ row.goalsFor }}:{{ row.goalsAgainst }}</td>
            <td><strong>{{ row.points }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.head { display: grid; gap: 0.35rem; }
.season { max-width: 360px; }
.fs-tabs :deep(a), .fs-tabs a {
  flex: 0 0 auto;
  color: var(--muted);
  font-weight: 800;
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 0.7rem 0.85rem;
  border-bottom: 3px solid transparent;
  text-decoration: none;
}
.fs-tabs a.on { color: var(--navy); border-bottom-color: var(--ice); }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.dense th, .dense td { padding: 0.62rem 0.4rem; }
.pos { color: var(--muted); width: 1.6rem; }
@media (max-width: 640px) {
  .wide { display: none; }
}
</style>
