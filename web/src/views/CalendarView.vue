<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'

const route = useRoute()
const router = useRouter()
const items = ref<any[]>([])
const tournaments = ref<Record<string, string>>({})
const error = ref('')
const loading = ref(true)
const tab = ref<'upcoming' | 'live' | 'played'>('upcoming')
const teams = useTeamDirectory()

onMounted(async () => {
  readTab()
  try {
    await teams.load()
    const [m, t] = await Promise.all([
      api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } }),
      api.get('/tournaments', { params: { size: 50 } }),
    ])
    items.value = m.data.content ?? []
    const names: Record<string, string> = {}
    for (const item of t.data.content ?? []) names[item.id] = item.name
    tournaments.value = names
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Календарь не загрузился.'
  } finally {
    loading.value = false
  }
})

watch(() => route.query.tab, readTab)

function readTab() {
  const value = String(route.query.tab || '')
  if (value === 'live' || value === 'played' || value === 'upcoming') tab.value = value
}

function setTab(next: 'upcoming' | 'live' | 'played') {
  tab.value = next
  router.replace({ query: next === 'upcoming' ? {} : { tab: next } })
}

const upcoming = computed(() =>
  items.value.filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED')
    .slice()
    .sort((a, b) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)))
)
const live = computed(() => items.value.filter((m) => m.status === 'LIVE' || m.status === 'PAUSED'))
const played = computed(() =>
  items.value.filter((m) => m.status === 'FINISHED' || m.status === 'CANCELLED')
    .slice()
    .sort((a, b) => String(b.scheduledAt).localeCompare(String(a.scheduledAt)))
)
const visible = computed(() => {
  if (tab.value === 'live') return live.value
  if (tab.value === 'played') return played.value
  return upcoming.value
})
const grouped = computed(() => {
  const map = new Map<string, any[]>()
  for (const match of visible.value) {
    const key = match.tournamentId || 'none'
    const list = map.get(key) ?? []
    list.push(match)
    map.set(key, list)
  }
  return [...map.entries()].map(([id, matches]) => ({
    id,
    name: tournaments.value[id] || 'Турнир',
    matches,
  }))
})
</script>

<template>
  <section class="stack page">
    <div class="page-title">
      <h1>Игры</h1>
    </div>
    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'upcoming' }" @click="setTab('upcoming')">Ближайшие</button>
      <button type="button" :class="{ on: tab === 'live' }" @click="setTab('live')">Live</button>
      <button type="button" :class="{ on: tab === 'played' }" @click="setTab('played')">Результаты</button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="skeleton" />
    <EmptyState
      v-else-if="!visible.length"
      :title="tab === 'live' ? 'Сейчас никто не играет' : tab === 'played' ? 'Прошедших матчей ещё нет' : 'Пока нет ближайших матчей'"
    />
    <div v-else class="sheet">
      <section v-for="group in grouped" :key="group.id">
        <div class="league-head">
          <div>
            {{ group.name }}
            <small>KRONBARS</small>
          </div>
        </div>
        <MatchRow
          v-for="m in group.matches"
          :key="m.id"
          :match="m"
          :home-name="teams.name(m.homeTeamId)"
          :away-name="teams.name(m.awayTeamId)"
        />
      </section>
    </div>
  </section>
</template>

<style scoped>
.page { gap: 0.55rem; }
.page-title { margin-bottom: 0; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
</style>
