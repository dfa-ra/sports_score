<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'

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
    <h1>{{ tournament.name }}</h1>
    <p>{{ tournament.description || 'Tournament details and table.' }}</p>
    <div class="panel">
      <h2>Standings</h2>
      <table class="table">
        <thead><tr><th>Team</th><th>P</th><th>W</th><th>D</th><th>L</th><th>Pts</th></tr></thead>
        <tbody>
          <tr v-for="row in standings" :key="row.teamId">
            <td>{{ row.teamName }}</td><td>{{ row.played }}</td><td>{{ row.wins }}</td>
            <td>{{ row.draws }}</td><td>{{ row.losses }}</td><td>{{ row.points }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="panel">
      <h2>Teams</h2>
      <div v-for="team in teams" :key="team.id" class="muted">{{ team.teamName }} · {{ team.status }}</div>
    </div>
  </section>
</template>
