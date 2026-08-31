<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import { formatWhen } from '../../lib/format'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const matches = ref<any[]>([])
const teams = useTeamDirectory()

const ordered = computed(() =>
  [...matches.value].sort((a, b) => {
    const rank = (status: string) => (status === 'LIVE' || status === 'PAUSED' ? 0 : status === 'SCHEDULED' ? 1 : 2)
    const byStatus = rank(a.status) - rank(b.status)
    if (byStatus !== 0) return byStatus
    return String(a.scheduledAt || '').localeCompare(String(b.scheduledAt || ''))
  }),
)

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
      <h1>Выберите матч</h1>
      <p>Только игры, на которые вас назначили. Дальше — часы, гол и карточки.</p>
    </div>
    <EmptyState v-if="!ordered.length" title="Нет назначений" text="Когда поставят на игру — карточка появится сама." />
    <div v-else class="grid cards">
      <RouterLink v-for="m in ordered" :key="m.id" class="panel card-link" :to="`/referee/matches/${m.id}`">
        <StatusBadge :status="m.status" />
        <div class="versus">{{ teams.fullName(m.homeTeamId) }} — {{ teams.fullName(m.awayTeamId) }}</div>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">{{ formatWhen(m.scheduledAt) }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.45rem; }
.versus { font-weight: 750; color: var(--text-strong); }
.score { font-weight: 800; color: var(--navy); font-size: 1.2rem; }
</style>
