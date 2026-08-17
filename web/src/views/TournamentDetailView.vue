<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const tournament = ref<any>(null)
const teams = ref<any[]>([])
const standings = ref<any[]>([])

onMounted(async () => {
  const id = route.params.id
  const [t, teamRes, standingRes] = await Promise.all([
    api.get(`/tournaments/${id}`),
    api.get(`/tournaments/${id}/teams`),
    api.get(`/tournaments/${id}/standings`),
  ])
  tournament.value = t.data
  teams.value = teamRes.data
  standings.value = standingRes.data
})
</script>

<template>
  <section v-if="tournament" class="stack">
    <div class="page-title">
      <StatusBadge :status="tournament.status" />
      <h1>{{ tournament.name }}</h1>
      <p>{{ tournament.description || 'Таблица, заявки и характер сезона — всё на одной странице.' }}</p>
    </div>
    <div class="panel">
      <h2>Таблица</h2>
      <EmptyState v-if="!standings.length" title="Ещё рано считать" text="Очки появятся после первых свистков." />
      <table v-else class="table">
        <thead>
          <tr><th>Команда</th><th>И</th><th>В</th><th>Н</th><th>П</th><th>Мячи</th><th>Очки</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in standings" :key="row.teamId">
            <td>
              <RouterLink :to="`/teams/${row.teamId}`">{{ row.teamName }}</RouterLink>
            </td>
            <td>{{ row.played }}</td>
            <td>{{ row.wins }}</td>
            <td>{{ row.draws }}</td>
            <td>{{ row.losses }}</td>
            <td>{{ row.goalsFor }}:{{ row.goalsAgainst }}</td>
            <td><strong>{{ row.points }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="panel stack">
      <h2>Команды</h2>
      <EmptyState v-if="!teams.length" title="Заявок нет" text="Капитаны ещё выбирают цвет формы." />
      <RouterLink v-for="team in teams" :key="team.id" class="row" :to="`/teams/${team.teamId || team.id}`">
        <span>{{ team.teamName || team.name }}</span>
        <StatusBadge :status="team.status" />
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.45rem; }
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 0.15rem;
  border-bottom: 1px solid var(--line);
  color: var(--text-strong);
  text-decoration: none;
}
.row:hover { color: var(--accent); }
</style>
