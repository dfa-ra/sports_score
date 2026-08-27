<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import EmptyState from '../components/EmptyState.vue'

const scorers = ref<any[]>([])
const assists = ref<any[]>([])
const keepers = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    let tournamentId
    try {
      const current = await api.get('/tournaments/current')
      tournamentId = current.data?.id
    } catch {
      tournamentId = undefined
    }
    const params = { tournamentId, limit: 30 }
    const [g, a, k] = await Promise.all([
      api.get('/statistics/scorers', { params }),
      api.get('/statistics/assists', { params }),
      api.get('/statistics/goalkeepers', { params }),
    ])
    scorers.value = g.data
    assists.value = a.data
    keepers.value = k.data
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Статистика</h1>
      <p>Бомбардиры, ассистенты и вратари по сухим матчам текущего турнира.</p>
    </div>
    <div v-if="loading" class="grid cards">
      <div class="skeleton" />
      <div class="skeleton" />
    </div>
    <template v-else>
      <div class="panel">
        <h2>Бомбардиры</h2>
        <EmptyState v-if="!scorers.length" title="Голов ещё нет" />
        <table v-else class="table">
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
      <div class="panel">
        <h2>Ассистенты</h2>
        <EmptyState v-if="!assists.length" title="Передач ещё нет" />
        <table v-else class="table">
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
      <div class="panel">
        <h2>Вратари</h2>
        <EmptyState v-if="!keepers.length" title="Сухих матчей ещё нет" text="Считаем по позиции вратаря и нулю пропущенных." />
        <table v-else class="table">
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
    </template>
  </section>
</template>
