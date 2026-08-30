<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { formatWhen } from '../lib/format'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const items = ref<any[]>([])
const error = ref('')
const loading = ref(true)
const tab = ref<'upcoming' | 'played'>('upcoming')
const teams = useTeamDirectory()

onMounted(async () => {
  try {
    await teams.load()
    const { data } = await api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } })
    items.value = data.content ?? []
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Календарь не загрузился.'
  } finally {
    loading.value = false
  }
})

const upcoming = computed(() =>
  items.value.filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED')
    .slice()
    .sort((a, b) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)))
)
const played = computed(() =>
  items.value.filter((m) => m.status === 'FINISHED' || m.status === 'CANCELLED')
    .slice()
    .sort((a, b) => String(b.scheduledAt).localeCompare(String(a.scheduledAt)))
)
const visible = computed(() => tab.value === 'upcoming' ? upcoming.value : played.value)
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Календарь</h1>
      <p>Ближайшие — всё, что ещё не закрыто. Прошедшие — уже в протоколе.</p>
    </div>
    <div class="filters">
      <button class="btn secondary" :class="{ on: tab === 'upcoming' }" @click="tab = 'upcoming'">Ближайшие</button>
      <button class="btn secondary" :class="{ on: tab === 'played' }" @click="tab = 'played'">Прошедшие</button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <EmptyState
      v-else-if="!visible.length"
      :title="tab === 'upcoming' ? 'Пока нет ближайших матчей' : 'Прошедших матчей ещё нет'"
    />
    <div v-else class="grid cards">
      <RouterLink
        v-for="m in visible"
        :key="m.id"
        class="panel match-card"
        :class="{ 'live-pulse': m.status === 'LIVE' }"
        :to="`/matches/${m.id}`"
      >
        <StatusBadge :status="m.status" />
        <div class="versus">{{ teams.name(m.homeTeamId) }} — {{ teams.name(m.awayTeamId) }}</div>
        <div class="score">{{ m.homeScore }} : {{ m.awayScore }}</div>
        <p class="muted">{{ formatWhen(m.scheduledAt) }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.match-card { display: grid; gap: 0.45rem; border-radius: 16px; }
.versus { font-weight: 750; color: var(--text-strong); }
.filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: var(--navy); border-color: transparent; border-radius: 999px; }
.filters .btn { border-radius: 999px; }
</style>
