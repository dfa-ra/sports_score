<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const matches = ref<any[]>([])
const teams = useTeamDirectory()

onMounted(async () => {
  await teams.load()
  const { data } = await api.get('/referee/matches')
  matches.value = data
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Пульт</p>
      <h1>Кабинет судьи</h1>
      <p>Только матчи, на которые вас назначили. Карманный свисток не прилагается.</p>
    </div>
    <EmptyState v-if="!matches.length" title="Нет назначений" text="Когда поставят на игру — карточка появится сама." />
    <div v-else class="grid cards">
      <RouterLink v-for="m in matches" :key="m.id" class="panel card-link" :to="`/referee/matches/${m.id}`">
        <StatusBadge :status="m.status" />
        <div class="versus">{{ teams.name(m.homeTeamId) }} — {{ teams.name(m.awayTeamId) }}</div>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">Открыть пульт</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.45rem; }
.versus { font-weight: 750; color: var(--text-strong); }
</style>
