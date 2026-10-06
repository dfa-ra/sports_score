<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../../api/client'
import KickoffWhen from '../../components/KickoffWhen.vue'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import { useAuthStore } from '../../stores/auth'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const matches = ref<any[]>([])
const teams = useTeamDirectory()
const auth = useAuthStore()

const ordered = computed(() =>
  [...matches.value].sort((a, b) => {
    const rank = (status: string) => (status === 'LIVE' || status === 'PAUSED' ? 0 : status === 'SCHEDULED' ? 1 : 2)
    const byStatus = rank(a.status) - rank(b.status)
    if (byStatus !== 0) return byStatus
    return String(a.scheduledAt || '').localeCompare(String(b.scheduledAt || ''))
  }),
)

function sideLabel(coverage: string) {
  if (coverage === 'HOME') return 'Хозяева'
  if (coverage === 'AWAY') return 'Гости'
  if (coverage === 'BOTH') return 'Обе команды'
  return ''
}

onMounted(async () => {
  await teams.load()
  const { data } = await api.get('/analyst/matches')
  matches.value = data ?? []
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <p class="eyebrow">Пульт</p>
      <h1>Аналитик</h1>
    </div>
    <EmptyState
      v-if="!ordered.length"
      :title="auth.canManageLeague ? 'Матчей нет' : 'Нет назначений'"
    />
    <div v-else class="grid cards">
      <RouterLink v-for="m in ordered" :key="m.id" class="panel card-link" :to="`/analyst/matches/${m.id}`">
        <StatusBadge :status="m.status" />
        <p v-if="m.status === 'FINISHED'" class="note">Завершён</p>
        <p v-else-if="m.status === 'CANCELLED'" class="note">Отменён</p>
        <p v-if="sideLabel(m.coverage)" class="note">{{ sideLabel(m.coverage) }}</p>
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
.note { margin: 0; color: var(--muted); font-weight: 800; font-size: 0.82rem; }
</style>
