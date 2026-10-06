<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import KickoffWhen from '../../components/KickoffWhen.vue'
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
  let tournamentId = ''
  try {
    const current = await api.get('/tournaments/current')
    tournamentId = current.data?.id || ''
  } catch {
    tournamentId = ''
  }
  const params = tournamentId
    ? { tournamentId, size: 100, sort: 'scheduledAt,asc' }
    : { size: 50, sort: 'scheduledAt,desc' }
  const { data } = await api.get('/matches', { params })
  matches.value = data.content ?? []
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Пульт</p>
      <h1>Аналитик</h1>
    </div>
    <EmptyState v-if="!ordered.length" title="Матчей нет" />
    <div v-else class="grid cards">
      <RouterLink v-for="m in ordered" :key="m.id" class="panel card-link" :to="`/analyst/matches/${m.id}`">
        <StatusBadge :status="m.status" />
        <div class="versus">{{ teams.fullName(m.homeTeamId) }} — {{ teams.fullName(m.awayTeamId) }}</div>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <KickoffWhen :at="m.scheduledAt" :status="m.status" :minute="m.minute" align="start" />
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card-link { display: grid; gap: 0.45rem; }
.versus { font-weight: 750; color: var(--text-strong); }
.score { font-weight: 800; color: var(--navy); font-size: 1.2rem; }
</style>
