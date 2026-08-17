<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'

const players = ref<any[]>([])
const teams = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [p, t] = await Promise.all([api.get('/statistics/players'), api.get('/statistics/teams')])
    players.value = p.data
    teams.value = t.data
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Статистика</h1>
      <p>Считаем только живые события. Отменённые голы в легенды не попадают.</p>
    </div>
    <div v-if="loading" class="grid cards">
      <div class="skeleton" />
      <div class="skeleton" />
    </div>
    <template v-else>
      <div class="panel">
        <h2>Игроки</h2>
        <EmptyState v-if="!players.length" title="Цифр ещё нет" text="Как только судья запишет первое событие — таблица оживёт." />
        <table v-else class="table">
          <thead><tr><th>Игрок</th><th>G</th><th>A</th><th>ЖК</th><th>КК</th><th>Игры</th></tr></thead>
          <tbody>
            <tr v-for="p in players" :key="p.playerId">
              <td>{{ p.displayName }}</td>
              <td>{{ p.goals }}</td>
              <td>{{ p.assists }}</td>
              <td>{{ p.yellowCards }}</td>
              <td>{{ p.redCards }}</td>
              <td>{{ p.appearances }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="panel">
        <h2>Команды</h2>
        <EmptyState v-if="!teams.length" title="Таблица ждёт первый матч" />
        <table v-else class="table">
          <thead><tr><th>Команда</th><th>В</th><th>Н</th><th>П</th><th>Очки</th></tr></thead>
          <tbody>
            <tr v-for="t in teams" :key="t.teamId">
              <td>{{ t.teamName }}</td>
              <td>{{ t.wins }}</td>
              <td>{{ t.draws }}</td>
              <td>{{ t.losses }}</td>
              <td><strong>{{ t.points }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
h2 { font-size: 1.2rem; margin-bottom: 0.4rem; }
</style>
