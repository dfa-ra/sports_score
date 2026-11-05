<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import CalendarMatchLine from '../components/CalendarMatchLine.vue'
import {
  applyCalendarFilter,
  boardLink,
  calendarCounts,
  calendarStageLabel,
  dayTitle,
  dottedDays,
  groupByTournament,
  heroMeta,
  isFavoriteMatch,
  matchesOnDay,
  rowClock,
  sortDayMatches,
  type CalendarFilter,
  type CalendarMatch,
  undatedMatches,
  weekdayChip,
  weekKeys,
} from '../lib/calendarFeed'
import { ymd } from '../lib/match'
import { shiftYmd } from '../lib/moscowTime'
import { collectPaged, DEFAULT_PAGE_CAP, nullKickoffScanStep, type PageBody } from '../lib/pagedList'
import { useFavorites } from '../stores/favorites'
import { useTeamDirectory } from '../lib/useTeamDirectory'

type TourInfo = { id: string; name: string; competitionStage?: string | null }

const route = useRoute()
const teams = useTeamDirectory()
const fav = useFavorites()

const weekRows = ref<CalendarMatch[]>([])
const nullRows = ref<CalendarMatch[]>([])
const liveRows = ref<CalendarMatch[]>([])
const pausedRows = ref<CalendarMatch[]>([])
const tournaments = ref<Record<string, TourInfo>>({})
const weekComplete = ref(false)
const nullComplete = ref(false)
const liveComplete = ref(false)
const pausedComplete = ref(false)
const loading = ref(true)
const inflight = ref(false)
const error = ref('')
const anchor = ref(ymd(new Date()))
const filter = ref<CalendarFilter>({ kind: 'all' })
let refreshTimer = 0
let requestId = 0

const liveOnly = computed(() => route.name === 'live')
const todayKey = computed(() => ymd(new Date()))
const week = computed(() => weekKeys(anchor.value))
const selectedDay = computed(() => (week.value.includes(anchor.value) ? anchor.value : week.value[0] || anchor.value))

function favorite(match: CalendarMatch) {
  return isFavoriteMatch(match, fav.hasMatch, fav.hasTeam)
}

const filteredWeek = computed(() => applyCalendarFilter(weekRows.value, filter.value.kind === 'undated' ? { kind: 'all' } : filter.value, favorite))
const filteredNull = computed(() => applyCalendarFilter(nullRows.value, filter.value.kind === 'undated' ? { kind: 'all' } : filter.value, favorite))
const filteredLive = computed(() => applyCalendarFilter(liveRows.value, filter.value.kind === 'undated' ? { kind: 'all' } : filter.value, favorite))

const modeRows = computed(() => {
  if (liveOnly.value) {
    const rows = applyCalendarFilter(
      [...liveRows.value, ...pausedRows.value],
      filter.value.kind === 'undated' ? { kind: 'all' } : filter.value,
      favorite,
    )
    if (filter.value.kind === 'undated') return undatedMatches(rows)
    return sortDayMatches(rows)
  }
  if (filter.value.kind === 'undated') return undatedMatches(filteredNull.value)
  return matchesOnDay(filteredWeek.value, filteredNull.value, selectedDay.value)
})

const countsReady = computed(() => {
  if (loading.value || inflight.value) return false
  if (liveOnly.value) return liveComplete.value && pausedComplete.value
  if (filter.value.kind === 'undated') return nullComplete.value
  return weekComplete.value && nullComplete.value
})

const counts = computed(() => calendarCounts(modeRows.value))
const meta = computed(() => heroMeta(counts.value, countsReady.value))
const dots = computed(() => (countsReady.value && filter.value.kind !== 'undated' && !liveOnly.value
  ? dottedDays(filteredWeek.value.concat(filteredNull.value), week.value)
  : []))

const groups = computed(() => groupByTournament(modeRows.value))
const chipTournaments = computed(() => {
  const ids = new Set<string>()
  for (const match of [...weekRows.value, ...nullRows.value, ...liveRows.value, ...pausedRows.value]) {
    if (match.tournamentId) ids.add(match.tournamentId)
  }
  return [...ids].map((id) => tournaments.value[id] || { id, name: matchName(id) }).sort((a, b) => a.name.localeCompare(b.name, 'ru'))
})

const eyebrow = computed(() => {
  if (liveOnly.value) return 'Игры · Live'
  if (filter.value.kind === 'undated') return 'Игры · Без даты'
  if (selectedDay.value === todayKey.value) return 'Игры · Сегодня'
  return 'Игры'
})

const title = computed(() => {
  if (liveOnly.value) return 'Сейчас'
  if (filter.value.kind === 'undated') return 'Дата уточняется'
  return dayTitle(selectedDay.value)
})

const showLiveSide = computed(() => !liveOnly.value && filter.value.kind !== 'undated')
const favoriteSide = computed(() => {
  const pool = liveOnly.value
    ? [...liveRows.value, ...pausedRows.value]
    : [...weekRows.value, ...nullRows.value]
  return sortDayMatches(pool.filter(favorite)).slice(0, 6)
})

function matchName(id: string) {
  return tournaments.value[id]?.name || weekRows.value.find((row) => row.tournamentId === id)?.tournamentName
    || nullRows.value.find((row) => row.tournamentId === id)?.tournamentName
    || 'Турнир'
}

function stageOf(groupId: string, match: CalendarMatch) {
  return calendarStageLabel(match, tournaments.value[groupId])
}

function linkOf(groupId: string, match: CalendarMatch) {
  const stage = String(match.stageLabel || '').trim()
  const playoff = !!stage || tournaments.value[groupId]?.competitionStage === 'PLAYOFF'
  return boardLink(playoff)
}

function homeName(match: CalendarMatch) {
  return teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')
}
function awayName(match: CalendarMatch) {
  return teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда')
}

async function loadPage(params: Record<string, string | number>) {
  const { data } = await api.get<PageBody<CalendarMatch>>('/matches', { params })
  return data
}

async function loadWindow(id: number) {
  const currentWeek = week.value
  const from = currentWeek[0]
  const to = currentWeek[currentWeek.length - 1]
  if (!from || !to) return
  const [weekResult, nullResult, liveResult, pausedResult] = await Promise.all([
    collectPaged((page) => loadPage({
      from,
      to,
      size: 100,
      page,
      sort: 'scheduledAt,asc',
    }), DEFAULT_PAGE_CAP),
    loadNullKickoffs(),
    collectPaged((page) => loadPage({ status: 'LIVE', size: 100, page, sort: 'scheduledAt,desc' }), DEFAULT_PAGE_CAP),
    collectPaged((page) => loadPage({ status: 'PAUSED', size: 100, page, sort: 'scheduledAt,desc' }), DEFAULT_PAGE_CAP),
  ])
  if (id !== requestId) return
  weekRows.value = weekResult.rows
  weekComplete.value = weekResult.complete
  nullRows.value = nullResult.rows
  nullComplete.value = nullResult.complete
  liveRows.value = liveResult.rows
  liveComplete.value = liveResult.complete
  pausedRows.value = pausedResult.rows
  pausedComplete.value = pausedResult.complete
}

async function loadNullKickoffs() {
  const rows: CalendarMatch[] = []
  for (let page = 0; page < DEFAULT_PAGE_CAP; page += 1) {
    const body = await loadPage({ size: 100, page, sort: 'scheduledAt,desc' })
    const batch = body?.content ?? []
    rows.push(...batch)
    const step = nullKickoffScanStep({ page, cap: DEFAULT_PAGE_CAP, body, batch })
    if (step === 'done-complete') return { rows, complete: true }
    if (step === 'done-incomplete') return { rows, complete: false }
  }
  return { rows, complete: false }
}

async function loadTournaments() {
  const { data } = await api.get('/tournaments', { params: { size: 50 } })
  const next: Record<string, TourInfo> = {}
  for (const item of data.content ?? []) {
    next[item.id] = { id: item.id, name: item.name, competitionStage: item.competitionStage }
  }
  tournaments.value = next
}

async function load(initial = false) {
  const id = ++requestId
  if (initial) loading.value = true
  inflight.value = true
  weekComplete.value = false
  nullComplete.value = false
  liveComplete.value = false
  pausedComplete.value = false
  error.value = ''
  try {
    await Promise.all([teams.load(), loadTournaments(), loadWindow(id)])
  } catch {
    if (id !== requestId) return
    error.value = 'Матчи не загрузились'
    weekComplete.value = false
    nullComplete.value = false
    liveComplete.value = false
    pausedComplete.value = false
  } finally {
    if (id === requestId) {
      loading.value = false
      inflight.value = false
    }
  }
}

function armRefresh() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    void load(false)
  }, liveOnly.value ? 4_000 : 12_000)
}

function pick(next: CalendarFilter) {
  filter.value = next
}

function shiftWeek(delta: number) {
  const from = selectedDay.value || anchor.value
  anchor.value = shiftYmd(from, delta * 7) || from
}

function jumpNearest() {
  const keys = week.value
  const future = keys.find((key) => key > selectedDay.value && dots.value.includes(key))
  const past = [...keys].reverse().find((key) => key < selectedDay.value && dots.value.includes(key))
  if (future || past) {
    anchor.value = (future || past) as string
    return
  }
  shiftWeek(1)
}

function tournamentOn(id: string) {
  return filter.value.kind === 'tournament' && filter.value.tournamentId === id
}

function hint(match: CalendarMatch) {
  return rowClock(match)
}

onMounted(async () => {
  await load(true)
  armRefresh()
})

watch(() => route.name, () => {
  if (!loading.value) armRefresh()
})

watch(() => week.value.join('|'), (next, prev) => {
  if (!prev || next === prev) return
  loading.value = true
  void load(false)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="calendar">
    <header class="hero">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="meta" class="meta">
        <span>{{ meta }}</span>
      </p>
      <p v-else-if="loading || inflight" class="meta quiet">Загрузка матчей…</p>
      <div v-if="!liveOnly && filter.kind !== 'undated'" class="week">
        <button type="button" class="arrow" aria-label="Предыдущая неделя" @click="shiftWeek(-1)">‹</button>
        <div class="days">
          <button
            v-for="key in week"
            :key="key"
            type="button"
            class="day"
            :class="{ on: key === selectedDay }"
            @click="anchor = key"
          >
            <span>{{ weekdayChip(key).weekday }}</span>
            <b>{{ weekdayChip(key).date }}</b>
            <i v-if="dots.includes(key)" class="dot" aria-hidden="true"></i>
          </button>
        </div>
        <button type="button" class="arrow" aria-label="Следующая неделя" @click="shiftWeek(1)">›</button>
      </div>
    </header>

    <div class="filters" role="tablist" aria-label="Фильтр матчей">
      <button type="button" :class="{ on: filter.kind === 'all' }" @click="pick({ kind: 'all' })">
        <span class="full">Все турниры</span><span class="short">Все</span>
      </button>
      <button type="button" :class="{ on: filter.kind === 'favorites' }" @click="pick({ kind: 'favorites' })">
        <span class="full">★ Избранные</span><span class="short">★ Избр.</span>
      </button>
      <button type="button" :class="{ on: filter.kind === 'undated' }" @click="pick({ kind: 'undated' })">Без даты</button>
      <button
        v-for="tour in chipTournaments"
        :key="tour.id"
        type="button"
        :class="{ on: tournamentOn(tour.id) }"
        @click="pick({ kind: 'tournament', tournamentId: tour.id })"
      >{{ tour.name }}</button>
    </div>

    <p v-if="!countsReady && !loading && !inflight && !error" class="note">Показаны не все матчи</p>

    <div class="layout">
      <div class="main-col">
        <div v-if="loading" class="card skeleton" role="status" aria-label="Загрузка матчей">
          <p>Загрузка матчей…</p>
          <span></span><span></span><span></span>
        </div>
        <div v-else-if="error && !groups.length" class="card state">
          <strong>Матчи не загрузились</strong>
          <button class="btn secondary" type="button" @click="load(true)">Повторить</button>
        </div>
        <div v-else-if="!groups.length" class="card state">
          <strong v-if="filter.kind === 'favorites'">Избранных пока нет</strong>
          <strong v-else-if="filter.kind === 'undated'">Матчей без даты нет</strong>
          <strong v-else-if="liveOnly">Сейчас никто не играет</strong>
          <strong v-else>Матчей на этот день пока нет</strong>
          <button v-if="filter.kind === 'all' && !liveOnly" class="btn secondary" type="button" @click="jumpNearest">Перейти</button>
        </div>
        <template v-else>
          <article v-if="filter.kind === 'undated'" class="card">
            <header class="card-head">
              <b>Дата уточняется</b>
            </header>
            <CalendarMatchLine
              v-for="match in groups.flatMap((group) => group.matches)"
              :key="match.id"
              :match="match"
              :kicker="matchName(match.tournamentId || '')"
              :home-name="homeName(match)"
              :away-name="awayName(match)"
              :home-logo="teams.logo(match.homeTeamId)"
              :away-logo="teams.logo(match.awayTeamId)"
            />
          </article>
          <article v-for="group in groups" v-else :key="group.id" class="card">
            <header class="card-head">
              <div>
                <b>{{ matchName(group.id) }}</b>
                <span>{{ stageOf(group.id, group.matches[0]) }}</span>
              </div>
              <RouterLink
                v-if="group.id !== 'none'"
                :to="{ path: `/tournaments/${group.id}`, query: { tab: linkOf(group.id, group.matches[0]).tab } }"
              >{{ linkOf(group.id, group.matches[0]).label }} →</RouterLink>
            </header>
            <CalendarMatchLine
              v-for="match in group.matches"
              :key="match.id"
              :match="match"
              :home-name="homeName(match)"
              :away-name="awayName(match)"
              :home-logo="teams.logo(match.homeTeamId)"
              :away-logo="teams.logo(match.awayTeamId)"
            />
          </article>
        </template>

        <aside class="fav-mobile card">
          <header class="card-head"><b>★ Избранные</b></header>
          <p v-if="!favoriteSide.length" class="empty">Избранных пока нет</p>
          <RouterLink v-for="match in favoriteSide" :key="`m-${match.id}`" class="mini" :to="`/matches/${match.id}`">
            <b>{{ homeName(match) }}</b>
            <span>{{ match.tournamentName || matchName(match.tournamentId || '') }}</span>
          </RouterLink>
        </aside>
      </div>

      <aside class="side">
        <section v-if="showLiveSide" class="card">
          <header class="card-head">
            <b>Live сейчас</b>
            <i v-if="liveComplete">{{ filteredLive.length }}</i>
          </header>
          <p v-if="!filteredLive.length" class="empty">Сейчас никто не играет</p>
          <RouterLink v-for="match in filteredLive" :key="match.id" class="mini" :to="`/matches/${match.id}`">
            <b>{{ homeName(match) }}</b>
            <span>{{ awayName(match) }}</span>
            <strong>{{ match.homeScore }} : {{ match.awayScore }}</strong>
          </RouterLink>
          <p v-if="!liveComplete && !loading" class="empty">Показаны не все матчи</p>
        </section>
        <section class="card fav-desk">
          <header class="card-head"><b>★ Избранные</b></header>
          <p v-if="!favoriteSide.length" class="empty">Избранных пока нет</p>
          <RouterLink v-for="match in favoriteSide" :key="match.id" class="mini" :to="`/matches/${match.id}`">
            <b>{{ homeName(match) }}</b>
            <span>{{ awayName(match) }} · {{ hint(match) }}</span>
          </RouterLink>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.calendar { display: grid; gap: 0.9rem; min-width: 0; }
.hero {
  background: var(--ink);
  color: #fff;
  border-radius: 22px;
  padding: 1.15rem 1.2rem 1rem;
}
.eyebrow {
  margin: 0;
  color: var(--ice);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.hero h1 {
  color: #fff;
  font-size: clamp(1.7rem, 3vw, 2.4rem);
  margin: 0.2rem 0 0.35rem;
  text-transform: none;
}
.meta { margin: 0; color: rgba(255,255,255,0.82); font-weight: 650; }
.meta.quiet { color: rgba(255,255,255,0.62); }
.week { display: flex; align-items: center; gap: 0.35rem; margin-top: 0.9rem; }
.days { display: flex; gap: 0.35rem; flex: 1; min-width: 0; justify-content: space-between; }
.day, .arrow {
  border: 0;
  background: rgba(255,255,255,0.06);
  color: #fff;
  border-radius: 14px;
  cursor: pointer;
}
.arrow { width: 32px; height: 48px; flex: 0 0 auto; font-size: 1.2rem; }
.day {
  flex: 1 1 0;
  min-width: 0;
  display: grid;
  justify-items: center;
  gap: 0.05rem;
  padding: 0.35rem 0.1rem 0.45rem;
  position: relative;
}
.day span { font-size: 0.68rem; letter-spacing: 0.04em; text-transform: uppercase; color: rgba(255,255,255,0.7); }
.day b { font-size: 1rem; }
.day.on { background: var(--ice); color: var(--navy); }
.day.on span { color: var(--navy); }
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ice);
  position: absolute;
  bottom: 4px;
}
.day.on .dot { background: var(--navy); }
.filters {
  display: flex;
  gap: 0.4rem;
  overflow-x: auto;
  position: sticky;
  top: 56px;
  z-index: 4;
  background: rgba(232, 238, 244, 0.94);
  padding: 0.35rem 0;
  scrollbar-width: none;
}
.filters::-webkit-scrollbar { display: none; }
.filters button {
  flex: 0 0 auto;
  border: 0;
  background: #fff;
  color: var(--navy);
  border-radius: 999px;
  padding: 0.42rem 0.8rem;
  font-weight: 800;
  cursor: pointer;
}
.filters button.on { background: var(--ice); }
.filters .short { display: none; }
.note { margin: 0; color: var(--muted); font-weight: 700; }
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 0.9rem;
  align-items: start;
}
.main-col, .side { display: grid; gap: 0.75rem; min-width: 0; }
.card {
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  min-width: 0;
}
.card-head {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: center;
  padding: 0.75rem 0.9rem;
  color: var(--navy);
}
.card-head b { font-size: 0.95rem; }
.card-head span { display: block; color: var(--muted); font-size: 0.78rem; font-weight: 700; }
.card-head a { color: var(--navy); font-size: 0.75rem; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; }
.card-head i {
  font-style: normal;
  background: var(--status-live-soft);
  color: var(--status-live-ink);
  border-radius: 999px;
  min-width: 1.4rem;
  text-align: center;
  font-weight: 800;
  font-size: 0.75rem;
}
.state, .skeleton { padding: 1.4rem 1rem; display: grid; justify-items: center; gap: 0.7rem; text-align: center; }
.skeleton span {
  display: block;
  width: 100%;
  height: 2.4rem;
  border-radius: 10px;
  background: linear-gradient(90deg, #e8eef5 25%, #f7f9fb 50%, #e8eef5 75%);
  background-size: 200% 100%;
}
.empty { margin: 0; padding: 0 0.9rem 0.9rem; color: var(--muted); }
.mini {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.1rem 0.5rem;
  padding: 0.45rem 0.9rem 0.55rem;
  color: inherit;
  text-decoration: none;
  border-top: 1px solid var(--line);
}
.mini b { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mini span { grid-column: 1 / -1; color: var(--muted); font-size: 0.75rem; }
.fav-mobile { display: none; }
@media (max-width: 720px) {
  .layout { grid-template-columns: 1fr; }
  .side { display: none; }
  .fav-mobile { display: block; }
  .filters .full { display: none; }
  .filters .short { display: inline; }
  .hero { border-radius: 18px; padding: 0.95rem 0.8rem 0.8rem; }
  .days { gap: 0.2rem; }
  .day { padding-inline: 0; }
}
</style>
