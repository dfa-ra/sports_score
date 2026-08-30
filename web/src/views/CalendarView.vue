<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { ymd } from '../lib/match'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'

const route = useRoute()
const router = useRouter()
const items = ref<any[]>([])
const tournaments = ref<Record<string, string>>({})
const error = ref('')
const loading = ref(true)
const tab = ref<'upcoming' | 'live' | 'played'>('upcoming')
const day = ref('')
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

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function weekday(date: Date) {
  return date.toLocaleDateString('ru-RU', { weekday: 'short' }).replace('.', '').toUpperCase()
}

const strip = computed(() => {
  const today = new Date()
  today.setHours(12, 0, 0, 0)
  const days = []
  for (let i = -2; i <= 2; i++) {
    const date = new Date(today)
    date.setDate(today.getDate() + i)
    const key = ymd(date)
    days.push({
      key,
      today: i === 0,
      label: i === 0
        ? `Сегодня ${pad(date.getDate())}.${pad(date.getMonth() + 1)}.`
        : `${weekday(date)} ${pad(date.getDate())}.${pad(date.getMonth() + 1)}.`,
    })
  }
  return days
})

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
  const base = tab.value === 'live' ? live.value : tab.value === 'played' ? played.value : upcoming.value
  if (!day.value) return base
  return base.filter((m) => ymd(m.scheduledAt) === day.value)
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
    <div class="date-strip">
      <button type="button" :class="{ on: !day }" @click="day = ''">Все</button>
      <button
        v-for="item in strip"
        :key="item.key"
        type="button"
        :class="{ on: day === item.key }"
        @click="day = item.key"
      >{{ item.label }}</button>
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
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
        <RouterLink class="sheet-link" to="/table">Таблица <span>›</span></RouterLink>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page { gap: 0; }
.date-strip { margin: 0 -0.2rem 0.15rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  margin-top: 0.45rem;
}
</style>
