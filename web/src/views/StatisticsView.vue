<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../api/client'
const players = ref<any[]>([])
const teams = ref<any[]>([])
onMounted(async () => {
  const [p, t] = await Promise.all([api.get('/statistics/players'), api.get('/statistics/teams')])
  players.value = p.data
  teams.value = t.data
})
</script>
<template>
  <section class="stack">
    <h1>Statistics</h1>
    <div class="panel">
      <h2>Players</h2>
      <table class="table">
        <thead><tr><th>Player</th><th>G</th><th>A</th><th>YC</th><th>RC</th><th>Apps</th></tr></thead>
        <tbody>
          <tr v-for="p in players" :key="p.playerId">
            <td>{{ p.displayName }}</td><td>{{ p.goals }}</td><td>{{ p.assists }}</td>
            <td>{{ p.yellowCards }}</td><td>{{ p.redCards }}</td><td>{{ p.appearances }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="panel">
      <h2>Teams</h2>
      <table class="table">
        <thead><tr><th>Team</th><th>W</th><th>D</th><th>L</th><th>Pts</th></tr></thead>
        <tbody>
          <tr v-for="t in teams" :key="t.teamId">
            <td>{{ t.teamName }}</td><td>{{ t.wins }}</td><td>{{ t.draws }}</td><td>{{ t.losses }}</td><td>{{ t.points }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
