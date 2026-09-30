<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import { ymd } from '../lib/match'
import EmptyState from '../components/EmptyState.vue'
import MatchRow from '../components/MatchRow.vue'

const route = useRoute()
const items = ref<any[]>([])
const tournaments = ref<Record<string, string>>({})
const error = ref('')
const loading = ref(true)
const day = ref('')
const mineOnly = ref(false)
const teams = useTeamDirectory()
const fav = useFavorites()
let refreshTimer = 0

const liveOnly = computed(() => route.name === 'live')

async function loadMatches() {
  const { data } = await api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } })
  items.value = data.content ?? []
}

onMounted(async () => {
  try {
    await teams.load()
    const [, t] = await Promise.all([
      loadMatches(),
      api.get('/tournaments', { params: { size: 50 } }),
    ])
    const names: Record<string, string> = {}
    for (const item of t.data.content ?? []) names[item.id] = item.name
    tournaments.value = names
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Календарь не загрузился.'
  } finally {
    loading.value = false
    refreshTimer = window.setInterval(() => {
      loadMatches().catch(() => {})
    }, 12_000)
  }
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

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
      label: i === 0
        ? `Сегодня ${pad(date.getDate())}.${pad(date.getMonth() + 1)}.`
        : `${weekday(date)} ${pad(date.getDate())}.${pad(date.getMonth() + 1)}.`,
    })
  }
  return days
})

function isMine(match: any) {
  return fav.hasMatch(match.id) || fav.hasTeam(match.homeTeamId) || fav.hasTeam(match.awayTeamId)
}

const visible = computed(() => {
  let rows: any[]
  if (liveOnly.value) {
    rows = items.value.filter((m) => m.status === 'LIVE' || m.status === 'PAUSED')
  } else if (day.value) {
    rows = items.value
      .filter((m) => ymd(m.scheduledAt) === day.value)
      .slice()
      .sort((a, b) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)))
  } else {
    rows = items.value
      .filter((m) => m.status === 'SCHEDULED' || m.status === 'LIVE' || m.status === 'PAUSED')
      .slice()
      .sort((a, b) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)))
  }
  return mineOnly.value ? rows.filter(isMine) : rows
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
    <div v-if="liveOnly" class="live-head">
      <h1>Live</h1>
    </div>
    <div class="date-strip">
      <button type="button" :class="{ on: mineOnly }" @click="mineOnly = !mineOnly">Мои</button>
      <template v-if="!liveOnly">
        <button type="button" :class="{ on: !day }" @click="day = ''">Все</button>
        <button
          v-for="item in strip"
          :key="item.key"
          type="button"
          :class="{ on: day === item.key }"
          @click="day = item.key"
        >{{ item.label }}</button>
      </template>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="skeleton" />
    <EmptyState
      v-else-if="!visible.length"
      :title="mineOnly ? 'В избранном таких матчей нет' : liveOnly ? 'Сейчас никто не играет' : day ? 'В этот день матчей нет' : 'Пока нет ближайших матчей'"
    />
    <div v-else class="sheet">
      <section v-for="group in grouped" :key="group.id">
        <div class="league-head">
          <div>
            {{ group.name }}
          </div>
        </div>
        <MatchRow
          v-for="m in group.matches"
          :key="m.id"
          :match="m"
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
        <RouterLink v-if="!liveOnly" class="sheet-link" :to="`/table`">К таблице ›</RouterLink>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page { gap: 0; }
.date-strip { margin: 0 -0.2rem 0.15rem; }
.live-head { padding: 0.35rem 0 0.15rem; }
.live-head h1 { font-size: 1.35rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  margin-top: 0.45rem;
}
</style>
