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
    <div class="page-title">
      <h1>Таблица</h1>
      <p>По умолчанию — текущий турнир. Команда открывается по названию.</p>
    </div>
    <label class="field">Турнир
      <select v-model="tournamentId">
        <option v-for="t in tournaments" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
    </label>
    <p v-if="tournament">
      <RouterLink :to="`/tournaments/${tournament.id}`">Регламент и сетка</RouterLink>
    </p>
    <div v-if="loading" class="skeleton" />
    <EmptyState v-else-if="!standings.length" title="Таблица пустая" text="Нет утверждённых команд или сыгранных матчей." />
    <div v-else class="panel">
      <table class="table">
        <thead>
          <tr><th>Команда</th><th>И</th><th>В</th><th>Н</th><th>П</th><th>З</th><th>Пр</th><th>О</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in standings" :key="row.teamId">
            <td><RouterLink :to="`/teams/${row.teamId}`">{{ row.teamName }}</RouterLink></td>
            <td>{{ row.played }}</td>
            <td>{{ row.wins }}</td>
            <td>{{ row.draws }}</td>
            <td>{{ row.losses }}</td>
            <td>{{ row.goalsFor }}</td>
            <td>{{ row.goalsAgainst }}</td>
            <td><strong>{{ row.points }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
