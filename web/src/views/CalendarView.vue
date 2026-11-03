<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import { matchInstant, ymd } from '../lib/match'
import EmptyState from '../components/EmptyState.vue'
import MatchGoalAlerts from '../components/MatchGoalAlerts.vue'
import MatchRow from '../components/MatchRow.vue'

const PAST_DAYS = 21
const PAGE_SIZE = 100

const route = useRoute()
const items = ref<any[]>([])
const tournaments = ref<Record<string, string>>({})
const error = ref('')
const loading = ref(true)
const mineOnly = ref(false)
const teams = useTeamDirectory()
const fav = useFavorites()
const stripEl = ref<HTMLElement | null>(null)
let refreshTimer = 0
let alignedStrip = false

const liveOnly = computed(() => route.name === 'live')

function atNoon(date = new Date()) {
  const next = new Date(date)
  next.setHours(12, 0, 0, 0)
  return next
}

function addDays(date: Date, days: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

const todayKey = computed(() => ymd(atNoon()))
const day = ref(todayKey.value)

function oldestKey(batch: any[]) {
  let oldest = ''
  for (const match of batch) {
    const key = ymd(match.scheduledAt)
    if (!key) continue
    if (!oldest || key < oldest) oldest = key
  }
  return oldest
}

async function loadMatches() {
  const fromKey = ymd(addDays(atNoon(), -PAST_DAYS))
  const collected: any[] = []
  for (let page = 0; page < 30; page += 1) {
    const { data } = await api.get('/matches', {
      params: { size: PAGE_SIZE, page, sort: 'scheduledAt,desc' },
    })
    const batch = data.content ?? []
    collected.push(...batch)
    const oldest = oldestKey(batch)
    const last = batch.length < PAGE_SIZE || page + 1 >= Number(data.totalPages ?? page + 1)
    if (last || (oldest && oldest < fromKey)) break
  }
  items.value = collected
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
    armRefresh()
  }
})

function armRefresh() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  const delay = route.name === 'live' ? 4_000 : 12_000
  refreshTimer = window.setInterval(() => {
    loadMatches().catch((reason: unknown) => {
      const message = (reason as { response?: { data?: { message?: string } } })?.response?.data?.message
      if (!items.value.length) error.value = message || 'Календарь не обновился.'
    })
  }, delay)
}

watch(() => route.name, () => {
  if (!loading.value) armRefresh()
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

function alignStrip() {
  const root = stripEl.value
  if (!root) return
  const selected = root.querySelector<HTMLElement>('button.on')
  if (!selected) return
  const left = selected.offsetLeft - (root.clientWidth - selected.clientWidth) / 2
  root.scrollLeft = Math.max(0, left)
}

watch(loading, (isLoading) => {
  if (isLoading || alignedStrip || liveOnly.value) return
  alignedStrip = true
  nextTick(alignStrip)
})

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function weekday(date: Date) {
  return date.toLocaleDateString('ru-RU', { weekday: 'short' }).replace('.', '').toUpperCase()
}

function windowStartKey() {
  return ymd(addDays(atNoon(), -PAST_DAYS))
}

function inWindow(match: any) {
  const key = ymd(matchInstant(match))
  const floor = windowStartKey()
  return !!key && !!floor && key >= floor
}

function preferredDay(keys: string[], today: string, current: string) {
  if (keys.includes(current)) return current
  if (keys.includes(today)) return today
  const upcoming = keys.find((key) => key >= today)
  if (upcoming) return upcoming
  return keys[keys.length - 1] || today
}

function selectDay(key: string) {
  if (!matchDates.value.includes(key)) return
  day.value = key
  nextTick(alignStrip)
}

const calendarMatches = computed(() => {
  const rows = items.value.filter(inWindow)
  return mineOnly.value ? rows.filter(isMine) : rows
})

const matchDates = computed(() => {
  const keys = new Set<string>()
  for (const match of calendarMatches.value) {
    const key = ymd(matchInstant(match))
    if (key) keys.add(key)
  }
  return [...keys].sort()
})

watch(matchDates, (keys) => {
  const next = preferredDay(keys, todayKey.value, day.value)
  if (next !== day.value) day.value = next
  if (!liveOnly.value) nextTick(alignStrip)
})

const strip = computed(() => {
  const current = todayKey.value
  return matchDates.value.map((key) => {
    const [yearText, monthText, dateText] = key.split('-')
    const year = Number(yearText)
    const month = Number(monthText)
    const date = Number(dateText)
    const parsed = new Date(year, month - 1, date, 12)
    return {
      key,
      label: key === current
        ? `Сегодня ${pad(date)}.${pad(month)}.`
        : `${weekday(parsed)} ${pad(date)}.${pad(month)}.`,
    }
  })
})

function isMine(match: any) {
  return fav.hasMatch(match.id) || fav.hasTeam(match.homeTeamId) || fav.hasTeam(match.awayTeamId)
}

const visible = computed(() => {
  if (liveOnly.value) {
    const rows = items.value.filter((m) => m.status === 'LIVE' || m.status === 'PAUSED')
    return mineOnly.value ? rows.filter(isMine) : rows
  }
  return calendarMatches.value
    .filter((m) => ymd(matchInstant(m)) === day.value)
    .slice()
    .sort((a, b) => String(matchInstant(a) || '').localeCompare(String(matchInstant(b) || '')))
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
      <MatchGoalAlerts />
    </div>
    <div class="calendar-bar">
      <button
        type="button"
        class="fav-toggle"
        :class="{ on: mineOnly }"
        :aria-pressed="mineOnly"
        @click="mineOnly = !mineOnly"
      >Избранные</button>
      <div v-if="!liveOnly" ref="stripEl" class="date-strip">
        <button
          v-for="item in strip"
          :key="item.key"
          type="button"
          :class="{ on: day === item.key }"
          @click="selectDay(item.key)"
        >{{ item.label }}</button>
      </div>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="skeleton" />
    <EmptyState
      v-else-if="!visible.length"
      :title="mineOnly ? 'В избранном таких матчей нет' : liveOnly ? 'Сейчас никто не играет' : matchDates.length ? 'В этот день матчей нет' : 'Матчей нет'"
    />
    <div v-else class="sheet">
      <section v-for="group in grouped" :key="group.id">
        <div class="league-head">
          <div>
            <RouterLink v-if="group.id !== 'none'" :to="`/tournaments/${group.id}`">{{ group.name }}</RouterLink>
            <template v-else>{{ group.name }}</template>
          </div>
        </div>
        <MatchRow
          v-for="m in group.matches"
          :key="m.id"
          :match="m"
          :home-name="teams.fullName(m.homeTeamId)"
          :away-name="teams.fullName(m.awayTeamId)"
        />
        <RouterLink
          v-if="!liveOnly"
          class="sheet-link"
          :to="group.id !== 'none' ? { path: '/table', query: { tournament: group.id } } : '/table'"
        >К таблице ›</RouterLink>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page {
  gap: 0;
  min-width: 0;
  max-width: 100%;
}
.calendar-bar {
  display: flex;
  align-items: stretch;
  flex-wrap: nowrap;
  gap: 0.35rem;
  margin: 0.35rem -0.2rem 0.15rem;
  min-width: 0;
  max-width: 100%;
}
.fav-toggle {
  flex: 0 0 auto;
  align-self: center;
  margin-left: 0.2rem;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--muted);
  font-weight: 800;
  font-size: 0.72rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  padding: 0.42rem 0.75rem;
  border-radius: 999px;
  cursor: pointer;
}
.fav-toggle.on {
  color: var(--navy);
  background: var(--ice);
  border-color: transparent;
}
.calendar-bar .date-strip {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  margin: 0;
}
@media (max-width: 720px) {
  .calendar-bar {
    margin-inline: 0;
    gap: 0.25rem;
  }
  .fav-toggle {
    margin-left: 0;
    padding: 0.38rem 0.55rem;
  }
  .league-head {
    min-width: 0;
    overflow-wrap: anywhere;
  }
}
.live-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
  flex-wrap: wrap;
  padding: 0.35rem 0 0.15rem;
}
.live-head h1 { font-size: 1.35rem; }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  margin-top: 0.45rem;
  min-width: 0;
  max-width: 100%;
}
</style>
