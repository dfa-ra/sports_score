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
const filter = ref<'ALL' | 'LIVE' | 'SCHEDULED' | 'FINISHED'>('ALL')
const teams = useTeamDirectory()

onMounted(async () => {
  try {
    await teams.load()
    const { data } = await api.get('/matches', { params: { size: 50, sort: 'scheduledAt,desc' } })
    items.value = data.content
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Матчи спрятались. Попробуем ещё раз чуть позже.'
  } finally {
    loading.value = false
  }
})

const visible = computed(() => {
  if (filter.value === 'ALL') return items.value
  if (filter.value === 'LIVE') return items.value.filter((m) => m.status === 'LIVE' || m.status === 'PAUSED')
  return items.value.filter((m) => m.status === filter.value)
})
</script>

<template>
  <section class="stack">
    <div class="page-title">
      <h1>Матчи</h1>
      <p>Live и расписание. Смотреть можно без регистрации.</p>
    </div>
    <div class="filters">
      <button class="btn secondary" :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">Все</button>
      <button class="btn secondary" :class="{ on: filter === 'LIVE' }" @click="filter = 'LIVE'">Live</button>
      <button class="btn secondary" :class="{ on: filter === 'SCHEDULED' }" @click="filter = 'SCHEDULED'">Скоро</button>
      <button class="btn secondary" :class="{ on: filter === 'FINISHED' }" @click="filter = 'FINISHED'">Сыграны</button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="grid cards">
      <div v-for="n in 4" :key="n" class="skeleton" />
    </div>
    <EmptyState v-else-if="!visible.length" title="Пока тишина" text="Ни одного матча в этом фильтре. Можно подождать или выбрать другой." />
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
.match-card { display: grid; gap: 0.45rem; }
.versus { font-weight: 750; color: var(--text-strong); }
.filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: var(--navy); border-color: transparent; border-radius: 999px; }
.filters .btn { border-radius: 999px; }
</style>
