<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import CalendarMatchLine from '../components/CalendarMatchLine.vue'
import ScrollFade from '../components/ScrollFade.vue'
import { distinctLabels } from '../lib/shortLabel'
import {
  applyCalendarFilter,
  boardLink,
  calendarCounts,
  calendarStageLabel,
  dayTitle,
  calendarHeroPending,
  calendarQueryPatch,
  calendarQueryState,
  dottedDays,
  groupByTournament,
  heroMeta,
  isFavoriteMatch,
  matchesOnDay,
  rowClock,
  sortDayMatches,
  type CalendarFilter,
  type CalendarMatch,
  sameSearchQuery,
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
const router = useRouter()
const opened = calendarQueryState(route.query as Record<string, unknown>, ymd(new Date()))
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
const error = ref('')
const anchor = ref(opened.date)
const filter = ref<CalendarFilter>(opened.tournamentId
  ? { kind: 'tournament', tournamentId: opened.tournamentId }
  : { kind: 'all' })
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
  if (loading.value) return false
  if (liveOnly.value) return liveComplete.value && pausedComplete.value
  if (filter.value.kind === 'undated') return nullComplete.value
  return weekComplete.value && nullComplete.value
})

const counts = computed(() => calendarCounts(modeRows.value))
const meta = computed(() => heroMeta(counts.value, countsReady.value))
const heroPending = computed(() => calendarHeroPending(loading.value, countsReady.value))
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
const chipLabels = computed(() => {
  const labels = distinctLabels(chipTournaments.value.map((tour) => tour.name))
  return new Map(chipTournaments.value.map((tour, index) => [tour.id, labels[index]]))
})

const showToday = computed(() => !liveOnly.value && filter.value.kind !== 'undated' && selectedDay.value === todayKey.value)
const phone = ref(false)
const pageEl = ref<HTMLElement | null>(null)
const bandEl = ref<HTMLElement | null>(null)
let phoneMedia: MediaQueryList | null = null
let bandObserver: ResizeObserver | null = null

function syncBar() {
  const height = bandEl.value?.offsetHeight || 0
  pageEl.value?.style.setProperty('--games-bar', `${height}px`)
}
function syncPhone() { phone.value = !!phoneMedia?.matches }

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

function markWindowOpen() {
  weekComplete.value = false
  nullComplete.value = false
  liveComplete.value = false
  pausedComplete.value = false
}

async function load(initial = false) {
  const id = ++requestId
  if (initial) {
    loading.value = true
    markWindowOpen()
  }
  error.value = ''
  try {
    await Promise.all([teams.load(), loadTournaments(), loadWindow(id)])
  } catch {
    if (id !== requestId) return
    error.value = 'Матчи не загрузились'
    markWindowOpen()
  } finally {
    if (id === requestId) loading.value = false
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

function syncCalendarQuery() {
  const next = calendarQueryPatch(route.query as Record<string, unknown>, anchor.value, filter.value)
  if (sameSearchQuery(next, route.query as Record<string, unknown>)) return
  void router.replace({ query: next })
}

function readCalendarQuery() {
  const next = calendarQueryState(route.query as Record<string, unknown>, anchor.value || ymd(new Date()))
  if (next.date !== anchor.value) anchor.value = next.date
  const currentId = filter.value.kind === 'tournament' ? filter.value.tournamentId : ''
  if (next.tournamentId !== currentId) {
    filter.value = next.tournamentId
      ? { kind: 'tournament', tournamentId: next.tournamentId }
      : { kind: 'all' }
  }
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
  phoneMedia = window.matchMedia('(max-width: 720px)')
  syncPhone()
  phoneMedia.addEventListener('change', syncPhone)
  syncBar()
  if (bandEl.value && typeof ResizeObserver !== 'undefined') {
    bandObserver = new ResizeObserver(() => syncBar())
    bandObserver.observe(bandEl.value)
  }
  syncCalendarQuery()
  await load(true)
  armRefresh()
})

watch([anchor, () => (filter.value.kind === 'tournament' ? filter.value.tournamentId : filter.value.kind)], () => {
  syncCalendarQuery()
})

watch(() => `${String(route.query.date || '')}|${String(route.query.tournament || '')}`, () => {
  readCalendarQuery()
})

watch(() => route.name, () => {
  if (!loading.value) armRefresh()
})

watch(() => week.value.join('|'), (next, prev) => {
  if (!prev || next === prev) return
  loading.value = true
  markWindowOpen()
  void load(false)
})

onUnmounted(() => {
  phoneMedia?.removeEventListener('change', syncPhone)
  bandObserver?.disconnect()
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <section ref="pageEl" class="calendar">
    <div ref="bandEl" class="hero-band">
    <header class="hero" :class="{ 'is-today': showToday }">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="meta" class="meta">
        <span class="today-bit">Сегодня · </span><span>{{ meta }}</span>
      </p>
      <p v-else-if="heroPending" class="meta quiet">Загрузка матчей…</p>
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

    <ScrollFade class="filters-fade" :arrows="!phone" :fade="phone ? '#001433' : '#e8eef4'">
      <div class="filters" role="tablist" aria-label="Фильтр матчей">
        <button type="button" :class="{ on: filter.kind === 'all' }" @click="pick({ kind: 'all' })">
          <span class="full chip-label">Все турниры</span><span class="short chip-label">Все</span>
        </button>
        <button type="button" :class="{ on: filter.kind === 'favorites' }" @click="pick({ kind: 'favorites' })">
          <span class="full chip-label">★ Избранные</span><span class="short chip-label">★ Избр.</span>
        </button>
        <button type="button" :class="{ on: filter.kind === 'undated' }" @click="pick({ kind: 'undated' })">
          <span class="chip-label">Без даты</span>
        </button>
        <button
          v-for="tour in chipTournaments"
          :key="tour.id"
          type="button"
          :class="{ on: tournamentOn(tour.id) }"
          :title="tour.name"
          :aria-label="tour.name"
          @click="pick({ kind: 'tournament', tournamentId: tour.id })"
        >
          <span class="chip-label" :title="tour.name">{{ chipLabels.get(tour.id) }}</span>
        </button>
      </div>
    </ScrollFade>
    </div>

    <p v-if="!countsReady && !loading && !error" class="note">Показаны не все матчи</p>

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
.hero-band { display: contents; }
.today-bit { display: none; }
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
.filters-fade {
  position: sticky;
  top: 56px;
  z-index: 4;
  background: rgba(232, 238, 244, 0.94);
}
.filters {
  display: flex;
  justify-content: flex-start;
  gap: 0.45rem;
  overflow-x: auto;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  background: transparent;
  padding: 0.35rem 0 0.45rem;
  scroll-padding-inline: 1rem 3.25rem;
  scrollbar-width: none;
}
.filters::before { content: ""; flex: 0 0 1rem; }
.filters::after { content: ""; flex: 0 0 3.25rem; }
.filters .chip-label { display: inline-block; max-width: 100%; vertical-align: bottom; }
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
  white-space: nowrap;
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
  gap: 0.85rem;
  align-items: center;
  padding: 0.8rem 1rem;
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
  .calendar { gap: 0; }
  .hero-band {
    display: flex;
    flex-direction: column;
    position: sticky;
    top: var(--chrome-h);
    z-index: 6;
    background: #001433;
    margin-inline: calc(var(--page-gutter) * -1);
    margin-top: -16px;
    width: calc(100% + 2 * var(--page-gutter));
    min-height: 150px;
    min-width: 0;
    max-width: none;
    overflow: hidden;
  }
  .hero, .week, .days, .filters-fade { min-width: 0; max-width: 100%; }
  .days { width: 100%; }
  .day { min-width: 0; }
  .hero {
    border-radius: 0;
    background: transparent;
    padding: 8px var(--page-gutter) 0;
    min-height: 0;
  }
  .hero.is-today .eyebrow { display: none; }
  .hero.is-today .today-bit { display: inline; }
  .hero h1 {
    font-size: 18px;
    line-height: 22px;
    font-weight: 800;
    margin: 0;
    white-space: nowrap;
  }
  .meta { margin: 0; font-size: 12px; line-height: 16px; font-weight: 600; white-space: normal; }
  .meta.quiet { white-space: nowrap; }
  .week { height: 44px; margin: 0; gap: 4px; }
  .days { gap: 4px; }
  .arrow { width: 32px; height: 44px; border-radius: 10px; font-size: 16px; }
  .day {
    height: 44px;
    border-radius: 10px;
    padding: 2px 0;
    gap: 0;
    background: #0d2552;
  }
  .day span { font-size: 10px; font-weight: 600; line-height: 12px; }
  .day b { font-size: 14px; font-weight: 800; line-height: 16px; }
  .dot { width: 4px; height: 4px; bottom: 3px; }
  .filters-fade {
    position: static;
    top: auto;
    height: 44px;
    margin-top: auto;
    padding: 0 var(--page-gutter);
    background: transparent;
  }
  .filters { height: 44px; align-items: center; gap: 8px; padding: 0; scroll-padding-inline: 0; }
  .filters::before, .filters::after { display: none; }
  .filters button {
    position: relative;
    height: 32px;
    padding: 0 14px;
    font-size: 12px;
    font-weight: 800;
    line-height: 16px;
    background: #0d2552;
    color: #fff;
    border: 1px solid #2c4677;
  }
  .filters button.on { background: #4cb4e5; color: var(--navy); border-color: #4cb4e5; }
  .filters button::before {
    content: "";
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: var(--tap-min);
    transform: translateY(-50%);
  }
  .layout { grid-template-columns: 1fr; margin-top: 12px; gap: 12px; }
  .main-col { gap: 12px; }
  .side { display: none; }
  .fav-mobile {
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-sizing: border-box;
    height: 60px;
    padding: 0 12px;
    border-radius: 12px;
  }
  .fav-mobile .card-head { padding: 0; gap: 8px; width: auto; }
  .fav-mobile .card-head b { font-size: 14px; font-weight: 800; line-height: 18px; }
  .fav-mobile .empty {
    display: block;
    height: auto;
    min-height: 0;
    max-height: none;
    padding: 0;
    margin: 0;
    border: 0;
    background: transparent;
    box-shadow: none;
    font-size: 12px;
    line-height: 16px;
    font-weight: 500;
    color: var(--muted);
  }
  .filters .full { display: none; }
  .filters .short { display: inline; }
  .card { border-radius: 12px; }
  .card.state, .skeleton {
    box-sizing: border-box;
    height: 68px;
    min-height: 60px;
    max-height: 68px;
    padding: 0 12px;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    text-align: left;
  }
  .card.state strong { font-size: 13.5px; font-weight: 700; line-height: 18px; }
  .card.state .btn { height: 44px; min-height: 44px; min-width: 100px; border-radius: 12px; }
  .card-head { gap: 8px; align-items: center; padding: 0 12px; height: 44px; }
  .days { gap: 4px; }
  .day { padding-inline: 0; }
  .week {
    height: 56px;
    gap: 0;
    align-items: stretch;
    overflow: visible;
  }
  .days {
    flex: 1 1 auto;
    gap: 6px;
    width: auto;
    overflow: hidden;
  }
  .day {
    flex: 1 1 0;
    height: 56px;
  }
  .arrow {
    position: relative;
    width: 32px;
    height: 56px;
    flex: 0 0 32px;
  }
  .arrow::before {
    content: "";
    position: absolute;
    left: 50%;
    top: 50%;
    width: 44px;
    height: 56px;
    transform: translate(-50%, -50%);
  }
  .filters-fade { position: relative; top: auto; }
  .filters-fade :deep(.scroll-fade-edge) { display: none; }
  .filters-fade.show-right::after {
    content: "";
    position: absolute;
    top: 0;
    right: 0;
    z-index: 3;
    width: 32px;
    height: 100%;
    pointer-events: none;
    background: linear-gradient(to right, transparent, #001433);
  }
  .filters-fade.show-left::before {
    content: "";
    position: absolute;
    top: 0;
    left: 0;
    z-index: 3;
    width: 24px;
    height: 100%;
    pointer-events: none;
    background: linear-gradient(to right, #001433, transparent);
  }
  .filters { gap: 6px; }
  .filters button {
    height: 40px;
    min-height: 40px;
    min-width: 48px;
    padding: 0 10px;
    border-radius: 20px;
    font-size: 12px;
  }
  .filters button::before {
    inset: -2px;
    width: auto;
    height: auto;
    transform: none;
  }
  .card-head {
    height: auto;
    min-height: 44px;
    overflow: visible;
    align-items: flex-start;
    padding: 14px 12px 8px;
  }
  .card-head > div { min-width: 0; flex: 1 1 auto; }
  .card-head b {
    display: block;
    margin: 0;
    font-size: 15px;
    line-height: 1.25;
    font-weight: 800;
    white-space: normal;
    overflow: visible;
    overflow-wrap: break-word;
  }
  .card-head a {
    white-space: nowrap;
    flex-shrink: 0;
    align-self: flex-start;
  }
  .main-col .card { scroll-margin-top: var(--games-bar, 0px); }
}
@media (max-width: 360px) {
  .days { gap: 4px; }
}
</style>
