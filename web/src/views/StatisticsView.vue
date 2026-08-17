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
    <div class="page-title">
      <h1>Статистика</h1>
      <p>Агрегация из событий матчей (не voided).</p>
    </div>
    <div class="panel">
      <h2>Игроки</h2>
      <div v-if="!players.length" class="empty" style="margin-top:0.75rem">Пока нет данных</div>
      <table v-else class="table">
        <thead><tr><th>Игрок</th><th>G</th><th>A</th><th>ЖК</th><th>КК</th><th>Игры</th></tr></thead>
        <tbody>
          <tr v-for="p in players" :key="p.playerId">
            <td>{{ p.displayName }}</td><td>{{ p.goals }}</td><td>{{ p.assists }}</td>
            <td>{{ p.yellowCards }}</td><td>{{ p.redCards }}</td><td>{{ p.appearances }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="panel">
      <h2>Команды</h2>
      <div v-if="!teams.length" class="empty" style="margin-top:0.75rem">Пока нет данных</div>
      <table v-else class="table">
        <thead><tr><th>Команда</th><th>W</th><th>D</th><th>L</th><th>Очки</th></tr></thead>
        <tbody>
          <tr v-for="t in teams" :key="t.teamId">
            <td>{{ t.teamName }}</td><td>{{ t.wins }}</td><td>{{ t.draws }}</td><td>{{ t.losses }}</td><td>{{ t.points }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
<style scoped>
h2 { font-size: 1.1rem; margin-bottom: 0.35rem; }
</style>
