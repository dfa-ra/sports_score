<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import { APPROVE_BUTTON_LABEL, entryActionError, showApproveButton } from '../lib/tournamentEntryActions'
import { formatLabel, isTournamentOpenForApply, matchListWhen, tournamentDatesError } from '../lib/format'
import { cardScoreText, rowClock } from '../lib/calendarFeed'
import { goalsPerMatchLabel } from '../lib/homeOverview'
import { matchInstant, matchStateLabel } from '../lib/match'
import { collectPaged } from '../lib/pagedList'
import {
  roundsHaveLive,
  tournamentDateSpan,
  tournamentHasPlayoff,
  tournamentStatusChip,
} from '../lib/playoffGrid'
import { setPageMeta } from '../lib/pageMeta'
import { registeredName } from '../lib/playerTitle'
import NotFoundView from './NotFoundView.vue'
import { publicTournamentDescription } from '../lib/tournamentCopy'
import AdminOnly from '../components/AdminOnly.vue'
import ApplyTeamDialog from '../components/ApplyTeamDialog.vue'
import CreateMatchForm from '../components/CreateMatchForm.vue'
import EmptyState from '../components/EmptyState.vue'
import PlayoffAdmin from '../components/PlayoffAdmin.vue'
import PlayoffBracket from '../components/PlayoffBracket.vue'
import CardsStandings from '../components/CardsStandings.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import MatchStageNote from '../components/MatchStageNote.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { parseStandings, standingGroupsHaveRows, type StandingGroup } from '../lib/standings'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const names = useTeamDirectory()
const tournament = ref<any>(null)
const publicDescription = computed(() => publicTournamentDescription(tournament.value?.description))
const teams = ref<any[]>([])
const teamsReady = ref(false)
const teamsError = ref(false)
const standings = ref<StandingGroup[]>([])
const tableDraft = ref<{ id?: string; name: string; teamIds: string[] }[]>([])
const matches = ref<any[]>([])
const matchesComplete = ref(true)
const summary = ref<{ matchesPlayed?: number; goals?: number; goalsPerMatch?: number | string | null } | null>(null)
const summaryError = ref(false)
const summaryLoading = ref(true)
const tableError = ref('')
const tableLoading = ref(true)
const scorers = ref<any[]>([])
const scorersError = ref(false)
const playoff = ref<any>(null)
const overview = ref<any>(null)
const playoffLoading = ref(false)
const playoffError = ref(false)
const name = ref('')
const description = ref('')
const status = ref('REGISTRATION')
const format = ref('ROUND_ROBIN')
const regulations = ref('')
const seasonYear = ref(2026)
const startDate = ref('')
const endDate = ref('')
type TourTab = 'playoff' | 'table' | 'matches' | 'teams' | 'rules'
const calendarFile = ref<File | null>(null)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const showMatchForm = ref(false)
const applying = ref(false)
const applyNote = ref('')
const missing = ref(false)

const registrationOpen = computed(() => isTournamentOpenForApply(tournament.value?.status))
const playedWhileRecruiting = computed(() =>
  tournament.value?.status === 'REGISTRATION'
  && matches.value.some((match) => match.status === 'LIVE' || match.status === 'PAUSED' || match.status === 'FINISHED'),
)
const showApply = computed(() =>
  registrationOpen.value
  && (auth.hasRole('CAPTAIN') || auth.canManageLeague || !auth.isAuthenticated)
)

onMounted(load)
watch(() => route.params.id, () => {
  load()
})

async function load() {
  const id = route.params.id
  if (!tournament.value || tournament.value.id !== id) {
    tournament.value = null
    overview.value = null
    playoff.value = null
    playoffLoading.value = false
    playoffError.value = false
    missing.value = false
    error.value = ''
    teams.value = []
    teamsReady.value = false
    teamsError.value = false
  }
  let detail: any
  try {
    await names.load()
    const [tourRes, overviewRes] = await Promise.all([
      api.get(`/tournaments/${id}`),
      api.get(`/tournaments/${id}/overview`).catch(() => ({ data: null })),
    ])
    detail = tourRes.data
    if (route.params.id === id) overview.value = overviewRes.data
  } catch (e: any) {
    if (route.params.id !== id) return
    const status = e?.response?.status
    if (status === 400 || status === 404) {
      tournament.value = null
      missing.value = true
      setPageMeta({
        title: 'Турнир не найден',
        description: 'Такого турнира нет.',
        path: `/tournaments/${id}`,
      })
      return
    }
    error.value = apiError(e, 'Турнир не загрузился.')
    return
  }
  if (route.params.id !== id) return
  missing.value = false
  const visibleDescription = publicTournamentDescription(detail.description)
  detail = { ...detail, description: visibleDescription }
  tournament.value = detail
  const playoffTask = loadPlayoff(String(id))
  try {
    tableLoading.value = true
    summaryLoading.value = true
    tableError.value = ''
    summaryError.value = false
    scorersError.value = false
    const teamTask = api.get(`/tournaments/${id}/teams`).then(
      (res) => ({ rows: res.data ?? [], failed: false as const }),
      () => ({ rows: [] as any[], failed: true as const }),
    )
    const restTask = Promise.all([
      api.get(`/tournaments/${id}/tables`),
      api.get('/statistics/summary', { params: { tournamentId: id } }).then((res) => res.data).catch(() => null),
      api.get('/statistics/board', { params: { tournamentId: id, limit: 50 } }).then((res) => res.data).catch(() => null),
      collectPaged((page) => api.get(`/tournaments/${id}/matches`, {
        params: { size: 100, page, sort: 'scheduledAt,asc' },
      }).then((res) => res.data), 5),
    ])
    const teamOutcome = await teamTask
    if (route.params.id !== id) return
    teams.value = teamOutcome.rows
    teamsError.value = teamOutcome.failed
    teamsReady.value = true
    const [tableRes, summaryData, boardData, matchPage] = await restTask
    if (route.params.id !== id) return
    summary.value = summaryData
    summaryError.value = summaryData == null
    summaryLoading.value = false
    scorers.value = boardData?.scorers ?? []
    scorersError.value = boardData == null
    matches.value = matchPage.rows
    matchesComplete.value = matchPage.complete
    try {
      const standingRes = await api.get(`/tournaments/${id}/standings`)
      if (route.params.id !== id) return
      standings.value = parseStandings(standingRes.data)
      tableError.value = ''
    } catch {
      if (route.params.id !== id) return
      standings.value = []
      tableError.value = 'Таблица не загрузилась'
    } finally {
      if (route.params.id === id) tableLoading.value = false
    }
    const approvedIds = new Set(
      (teamOutcome.rows ?? [])
        .filter((team: { status?: string }) => team.status === 'APPROVED')
        .map((team: { teamId?: string }) => String(team.teamId)),
    )
    tableDraft.value = (tableRes.data ?? []).map((row: any) => ({
      id: row.id,
      name: row.name,
      teamIds: (row.teamIds ?? []).map(String).filter((id: string) => approvedIds.has(id)),
    }))
    error.value = ''
  } catch (e: any) {
    if (route.params.id !== id) return
    error.value = apiError(e, 'Турнир не загрузился.')
    tableLoading.value = false
    summaryLoading.value = false
    if (!summary.value) summaryError.value = true
    teamsReady.value = true
  }
  await playoffTask
  if (route.params.id !== id) return
  publishTournamentTitle(detail?.name, String(id))
  name.value = detail.name
  description.value = visibleDescription
  status.value = detail.status
  format.value = detail.format
  regulations.value = detail.regulations || ''
  seasonYear.value = detail.seasonYear
  startDate.value = detail.startDate || ''
  endDate.value = detail.endDate || ''
}

function onApplied(message: string) {
  applyNote.value = message
  load()
}

async function save(event?: Event) {
  error.value = ''
  const form = event?.currentTarget as HTMLFormElement | undefined
  const dates = form ? [...form.querySelectorAll('input[type="date"]')] as HTMLInputElement[] : []
  const start = dates[0]?.value || startDate.value
  const end = dates[1]?.value || endDate.value
  startDate.value = start
  endDate.value = end
  const dateError = tournamentDatesError(start, end)
  if (dateError) {
    error.value = dateError
    return
  }
  pending.value = true
  try {
    await api.put(`/tournaments/${tournament.value.id}`, {
      name: name.value,
      description: description.value || undefined,
      status: status.value,
      format: format.value,
      regulations: regulations.value || undefined,
      seasonYear: Number(seasonYear.value),
      startDate: start || undefined,
      endDate: end || undefined,
    })
    ok.value = 'Турнир обновлён.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function approve(id: string) {
  const tournamentId = tournament.value?.id
  if (!tournamentId) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/tournaments/${tournamentId}/teams/${id}/approve`)
    await load()
  } catch (e: unknown) {
    error.value = entryActionError(e)
  } finally {
    pending.value = false
  }
}

async function restore(id: string) {
  const tournamentId = tournament.value?.id
  if (!tournamentId) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/tournaments/${tournamentId}/teams/${id}/restore`)
    await load()
  } catch (e: unknown) {
    error.value = entryActionError(e)
  } finally {
    pending.value = false
  }
}

async function importCalendar() {
  if (!calendarFile.value) return
  error.value = ''
  pending.value = true
  try {
    const form = new FormData()
    form.append('file', calendarFile.value)
    const { data } = await api.post(`/tournaments/${tournament.value.id}/calendar/import`, form)
    ok.value = `Календарь: создано ${data.created}, обновлено ${data.updated ?? 0}, пропущено ${data.skipped}.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

const LETTERS = 'ABCDEFGHIJKLMNOP'

function setTableCount(count: number) {
  const next = Math.max(0, Math.min(16, Number(count) || 0))
  const draft = tableDraft.value.slice(0, next)
  while (draft.length < next) {
    draft.push({ name: `Группа ${LETTERS[draft.length] || draft.length + 1}`, teamIds: [] })
  }
  tableDraft.value = draft
}

function toggleTeam(tableIndex: number, teamId: string) {
  tableDraft.value = tableDraft.value.map((table, i) => {
    const has = table.teamIds.includes(teamId)
    if (i === tableIndex) {
      return { ...table, teamIds: has ? table.teamIds.filter((id) => id !== teamId) : [...table.teamIds, teamId] }
    }
    return has ? { ...table, teamIds: table.teamIds.filter((id) => id !== teamId) } : table
  })
}

function teamInTable(teamId: string) {
  return tableDraft.value.findIndex((table) => table.teamIds.includes(teamId))
}

async function saveTables() {
  error.value = ''
  pending.value = true
  try {
    await api.put(`/tournaments/${tournament.value.id}/tables`, {
      tables: tableDraft.value.map((table) => ({
        id: table.id,
        name: table.name.trim(),
        teamIds: table.teamIds,
      })),
    })
    ok.value = 'Таблицы турнира сохранены.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

const savedTables = computed(() =>
  tableDraft.value
    .filter((table) => table.id)
    .map((table) => ({ id: table.id as string, name: table.name, teamIds: table.teamIds }))
)
const assignableTeams = computed(() => teams.value.filter((team) => team.status === 'APPROVED'))
const listedTeams = computed(() => teams.value.filter((team) => team.status !== 'WITHDRAWN'))
const withdrawnTeams = computed(() => teams.value.filter((team) => team.status === 'WITHDRAWN'))
const showGrid = computed(() => tournamentHasPlayoff({
  format: overview.value?.format || tournament.value?.format,
  stage: overview.value?.stage || playoff.value?.competitionStage || tournament.value?.competitionStage,
  rounds: playoff.value?.rounds,
  hasPlayoff: overview.value?.hasPlayoff,
}))
const playoffKnown = computed(() => {
  if (overview.value != null) return true
  if (!tournament.value) return false
  return !playoffLoading.value
})
const tab = computed<TourTab | 'pending'>(() => {
  const raw = String(route.query.tab || '')
  if (raw === 'matches' || raw === 'teams' || raw === 'rules' || raw === 'table') return raw
  // Until the overview (or the bracket) arrives, do not paint «Таблица» and then jump to «Сетка».
  if (!playoffKnown.value) return 'pending'
  if (raw === 'playoff') return showGrid.value ? 'playoff' : 'table'
  return showGrid.value ? 'playoff' : 'table'
})
const statusChip = computed(() => tournamentStatusChip(tournament.value?.status))
const hasRegulations = computed(() => !!String(tournament.value?.regulations || '').trim())
const teamsCount = computed(() => listedTeams.value.length)

function publishTournamentTitle(name?: string | null, id?: string) {
  const title = (name || '').trim()
  if (!title) return
  const path = `/tournaments/${id || route.params.id}`
  const apply = () => {
    if ((tournament.value?.name || '').trim() !== title) return
    setPageMeta({
      title,
      description: `Лига ИТМО: ${title}`,
      path,
    })
  }
  apply()
  void nextTick(apply)
}

watch(() => [route.fullPath, tournament.value?.name, missing.value] as const, () => {
  if (missing.value || !tournament.value?.name) return
  publishTournamentTitle(tournament.value.name, String(route.params.id || ''))
})
const matchesCount = computed(() => overview.value?.matchesCount ?? matches.value.length)
const championMeta = computed(() => {
  const name = String(tournament.value?.name || overview.value?.name || '').trim()
  const seasonLabel = String(overview.value?.seasonLabel || tournament.value?.seasonLabel || '').trim()
  const year = overview.value?.seasonYear ?? tournament.value?.seasonYear
  const season = seasonLabel || (typeof year === 'number' && year > 0 ? `Сезон ${year}` : '')
  return [name, season].filter(Boolean).join(' · ')
})
const heroFacts = computed(() => {
  const seasonLabel = String(overview.value?.seasonLabel || tournament.value?.seasonLabel || '').trim()
  const seasonYear = overview.value?.seasonYear ?? tournament.value?.seasonYear
  const season = seasonLabel || (seasonYear ? `Сезон ${seasonYear}` : '')
  const format = tournament.value?.format ? (formatLabel[tournament.value.format] || '') : ''
  const dates = tournamentDateSpan(tournament.value?.startDate, tournament.value?.endDate)
  return [season, format, dates].filter(Boolean).join(' · ')
})
const stageLine = computed(() => {
  const stage = overview.value?.stage || playoff.value?.competitionStage || tournament.value?.competitionStage
  if (stage === 'PLAYOFF') return 'Плей-офф'
  if (stage === 'GROUPS') return 'Групповой этап'
  return ''
})
const perMatchLabel = computed(() => {
  const raw = summary.value?.goalsPerMatch
  const value = typeof raw === 'number' ? raw : Number(raw)
  if (!Number.isFinite(value)) return ''
  return goalsPerMatchLabel(value)
})
const advancingPlaces = computed(() => Array.isArray(playoff.value?.advancingPlaces) ? playoff.value.advancingPlaces as number[] : [])
const upcoming = computed(() => matches.value
  .filter((match) => match.status === 'SCHEDULED' || match.status === 'LIVE' || match.status === 'PAUSED')
  .slice()
  .sort((a, b) => {
    const left = matchInstant(a)
    const right = matchInstant(b)
    if (!left && !right) return 0
    if (!left) return 1
    if (!right) return -1
    return String(left).localeCompare(String(right))
  })
  .slice(0, 6))
function clubName(teamId?: string | null) {
  if (!teamId) return ''
  return names.teams.value[teamId]?.name || ''
}
const playoffLive = computed(() => roundsHaveLive(playoff.value?.rounds) || overview.value?.playoffLive === true)

async function reloadTable() {
  const id = String(tournament.value?.id || route.params.id || '')
  if (!id) return
  tableLoading.value = true
  tableError.value = ''
  try {
    const standingRes = await api.get(`/tournaments/${id}/standings`)
    if (String(route.params.id) !== id) return
    standings.value = parseStandings(standingRes.data)
  } catch {
    tableError.value = 'Таблица не загрузилась'
  } finally {
    tableLoading.value = false
  }
}

async function reloadTeams() {
  const id = String(route.params.id || '')
  if (!id) return
  teamsReady.value = false
  teamsError.value = false
  try {
    const { data } = await api.get(`/tournaments/${id}/teams`)
    if (String(route.params.id) !== id) return
    teams.value = data ?? []
    teamsError.value = false
  } catch {
    if (String(route.params.id) !== id) return
    teams.value = []
    teamsError.value = true
  } finally {
    if (String(route.params.id) === id) teamsReady.value = true
  }
}

async function reloadSummary() {
  const id = String(tournament.value?.id || route.params.id || '')
  if (!id) return
  summaryLoading.value = true
  summaryError.value = false
  try {
    const { data } = await api.get('/statistics/summary', { params: { tournamentId: id } })
    if (String(route.params.id) !== id) return
    summary.value = data
  } catch {
    summaryError.value = true
  } finally {
    summaryLoading.value = false
  }
}

function setTab(next: TourTab) {
  router.replace({ query: { ...route.query, tab: next } })
}

let playoffRequest = 0
async function loadPlayoff(id: string, quiet = false) {
  const request = ++playoffRequest
  if (!quiet) {
    playoffLoading.value = true
    playoffError.value = false
  }
  try {
    const { data } = await api.get(`/tournaments/${id}/playoff`)
    if (request !== playoffRequest || route.params.id !== id) return
    playoff.value = data
    playoffError.value = false
  } catch {
    if (request !== playoffRequest || route.params.id !== id) return
    if (!quiet) {
      playoff.value = null
      playoffError.value = true
    }
  } finally {
    if (request === playoffRequest && route.params.id === id) playoffLoading.value = false
  }
}

let publishedRequest = 0

/** Slot save already updated the bracket card. Refresh every other tournament tab from the server. */
async function refreshAfterScheduleSave() {
  const id = String(tournament.value?.id || '')
  if (!id) return
  const request = ++publishedRequest
  void loadPlayoff(id, true)
  try {
    const [teamRes, standingRes, matchPage, overviewRes, summaryData] = await Promise.all([
      api.get(`/tournaments/${id}/teams`),
      api.get(`/tournaments/${id}/standings`),
      collectPaged((page) => api.get(`/tournaments/${id}/matches`, {
        params: { size: 100, page, sort: 'scheduledAt,asc' },
      }).then((res) => res.data), 5),
      api.get(`/tournaments/${id}/overview`).catch(() => ({ data: null })),
      api.get('/statistics/summary', { params: { tournamentId: id } }).then((res) => res.data).catch(() => null),
    ])
    if (request !== publishedRequest || String(route.params.id) !== id) return
    teams.value = teamRes.data ?? []
    standings.value = parseStandings(standingRes.data)
    matches.value = matchPage.rows
    matchesComplete.value = matchPage.complete
    summary.value = summaryData
    summaryError.value = summaryData == null
    summaryLoading.value = false
    if (overviewRes.data) overview.value = overviewRes.data
  } catch {
    // The bracket card already shows the saved kickoff. Leave the other tabs as they were.
  }
}

async function exclude(team: { teamId: string; teamName?: string }) {
  const name = team.teamName || 'команду'
  if (!confirm(`Убрать «${name}» из турнира? Заявка пропадёт из списка и из таблиц. Несыгранные матчи этой команды в турнире удалятся. Сыгранные останутся в календаре, но из зачёта таблицы уйдут.`)) return
  error.value = ''
  pending.value = true
  try {
    await api.delete(`/tournaments/${tournament.value.id}/teams/${team.teamId}`)
    ok.value = `${name} убрана из турнира.`
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <NotFoundView v-if="missing" heading="Турнир не найден" />
  <section v-else-if="!tournament && error" class="match-state">
    <strong>Турнир не загрузился</strong>
    <button class="btn secondary" type="button" @click="load">Повторить</button>
  </section>
  <section v-else class="stack tour">
    <header class="tour-hero">
      <div v-if="tournament" class="tour-copy">
        <RouterLink to="/tournaments">‹ Турниры</RouterLink>
        <h1>{{ tournament.name }}</h1>
        <p class="tour-line">
          <span v-if="statusChip" class="tour-st" :class="statusChip.kind">{{ statusChip.text }}</span>
          <span v-if="heroFacts">{{ heroFacts }}</span>
        </p>
        <p v-if="stageLine" class="stage">{{ stageLine }}</p>
      </div>
      <div v-else class="title-pending" role="status" aria-busy="true" aria-label="Заголовок загружается">
        <span class="chip-skel"></span>
        <span class="title-skel"></span>
        <span class="meta-skel"></span>
      </div>
      <div v-if="tournament" class="figures">
        <template v-if="summaryLoading">
          <span class="fig-skel"></span>
          <span class="fig-skel"></span>
          <span class="fig-skel"></span>
        </template>
        <div v-else-if="summaryError" class="fig-error">
          <p>Цифры не загрузились</p>
          <button class="btn secondary" type="button" @click="reloadSummary">Повторить</button>
        </div>
        <template v-else>
          <div><b>{{ summary?.matchesPlayed ?? 0 }}</b><span><span class="lf">Сыграно матчей</span><span class="ls">Сыграно</span></span></div>
          <div><b>{{ summary?.goals ?? 0 }}</b><span>Голы</span></div>
          <div><b>{{ perMatchLabel }}</b><span><span class="lf">Голов за матч</span><span class="ls">Г/матч</span></span></div>
        </template>
      </div>
    </header>
    <p v-if="publicDescription" class="lede">{{ publicDescription }}</p>

    <div v-if="showApply" class="panel stack">
      <h2>Заявить команду</h2>
      <p>Пока идёт набор, капитан и админ могут подать заявку. В сетку команду всё равно допускает админ.</p>
      <p v-if="applyNote" class="form-ok">{{ applyNote }}</p>
      <button v-if="auth.isAuthenticated" class="btn" type="button" @click="applying = true">Заявиться</button>
      <RouterLink v-else class="btn" :to="{ name: 'login', query: { redirect: route.fullPath } }">
        Войти и заявиться
      </RouterLink>
    </div>

    <div class="tab-card">
    <div class="ttabs" role="tablist" aria-label="Разделы турнира">
      <button v-if="showGrid || tab === 'pending'" type="button" role="tab" :aria-selected="tab === 'playoff'" :class="{ on: tab === 'playoff' }" @click="tab !== 'pending' && setTab('playoff')">
        Сетка
        <span v-if="playoffLive" class="dot" role="img" aria-label="Есть live"></span>
      </button>
      <button type="button" role="tab" :aria-selected="tab === 'table'" :class="{ on: tab === 'table' }" @click="setTab('table')">Таблица</button>
      <button type="button" role="tab" :aria-selected="tab === 'matches'" :class="{ on: tab === 'matches' }" @click="setTab('matches')">
        Матчи <i v-if="overview">{{ matchesCount }}</i>
      </button>
      <button type="button" role="tab" :aria-selected="tab === 'teams'" :class="{ on: tab === 'teams' }" @click="setTab('teams')">
        Команды <i v-if="teamsReady && !teamsError">{{ teamsCount }}</i>
      </button>
      <button type="button" role="tab" :aria-selected="tab === 'rules'" :class="{ on: tab === 'rules' }" @click="setTab('rules')">Регламент</button>
    </div>
    </div>

    <div v-if="tab === 'pending' || !tournament" class="panel tour-skel" role="status" aria-busy="true" aria-label="Турнир загружается">
      <span class="card-skel"></span>
      <span class="card-skel short"></span>
    </div>
    <div v-else-if="tab === 'rules'" class="panel" :class="{ 'rules-blank': !hasRegulations }" role="tabpanel">
      <div v-if="!hasRegulations" class="rules-empty">
        <strong>Регламент пока не опубликован</strong>
        <p>Организаторы добавят его позже</p>
      </div>
      <template v-else>
        <h2>Регламент</h2>
        <p class="rules-body">{{ tournament.regulations }}</p>
      </template>
    </div>
    <div v-else-if="tab === 'table'" class="table-layout" role="tabpanel">
      <div class="table-main card-block">
        <div v-if="tableLoading" class="table-skel" role="status" aria-busy="true">Таблица загружается…</div>
        <div v-else-if="tableError" class="table-state">
          <strong>Таблица не загрузилась</strong>
          <button class="btn secondary" type="button" @click="reloadTable">Повторить</button>
        </div>
        <div v-else-if="!standingGroupsHaveRows(standings)" class="table-state">
          <strong>Таблица пустая</strong>
          <p>Нет утверждённых команд или сыгранных матчей.</p>
        </div>
        <template v-else>
          <CardsStandings :tables="standings" :places="advancingPlaces" />
          <p v-if="advancingPlaces.length" class="legend"><i class="ice"></i> Выходят в плей-офф</p>
          <p class="legend keys">И — игры, В — победы, Н — ничьи, П — поражения, О — очки</p>
        </template>
      </div>
      <aside class="table-side">
        <section class="card-block">
          <header>
            <b>Ближайшие</b>
            <button type="button" @click="setTab('matches')">Все →</button>
          </header>
          <p v-if="!matchesComplete" class="side-note">Показаны не все матчи</p>
          <p v-if="!upcoming.length" class="side-note">Ближайших матчей нет</p>
          <RouterLink v-for="match in upcoming" :key="match.id" class="soon" :to="`/matches/${match.id}`">
            <span>
              <b>{{ rowClock(match) }}</b>
              <small>{{ match.homeTeamName || names.fullName(match.homeTeamId, 'Команда') }} — {{ match.awayTeamName || names.fullName(match.awayTeamId, 'Команда') }}</small>
            </span>
            <em>{{ cardScoreText(match) }}</em>
          </RouterLink>
        </section>
        <section class="card-block">
          <header>
            <b>Бомбардиры</b>
            <RouterLink :to="{ path: '/table', query: { tournament: tournament.id, tab: 'scorers' } }">Все →</RouterLink>
          </header>
          <div v-if="scorersError" class="table-state">
            <strong>Бомбардиры не загрузились</strong>
            <button class="btn secondary" type="button" @click="load">Повторить</button>
          </div>
          <p v-else-if="!scorers.length" class="side-note">Бомбардиров пока нет</p>
          <RouterLink v-for="(row, index) in scorers.slice(0, 8)" :key="row.playerId" class="scorer" :to="`/players/${row.playerId}`">
            <span class="pos">{{ index + 1 }}</span>
            <PlayerAvatar :src="row.avatarUrl" :name="registeredName(row)" :first-name="row.firstName" :last-name="row.lastName" :size="28" />
            <span>
              <b>{{ registeredName(row) }}</b>
              <small v-if="clubName(row.teamId)">{{ clubName(row.teamId) }}</small>
            </span>
            <strong>{{ row.goals }}</strong>
          </RouterLink>
        </section>
      </aside>
    </div>
    <div v-else-if="tab === 'teams'" class="panel stack" role="tabpanel">
      <h2>Команды</h2>
      <div v-if="!teamsReady" class="skeleton" role="status" aria-busy="true" aria-label="Заявки загружаются" />
      <div v-else-if="teamsError" class="table-state">
        <strong>Заявки не загрузились</strong>
        <button class="btn secondary" type="button" @click="reloadTeams">Повторить</button>
      </div>
      <EmptyState v-else-if="!listedTeams.length" title="Заявок нет" />
      <div v-for="team in listedTeams" :key="team.id" class="row">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
      </div>
    </div>
    <div v-else-if="tab === 'matches'" class="panel stack" role="tabpanel">
      <h2>Матчи</h2>
      <p v-if="!matchesComplete" class="side-note">Показаны не все матчи</p>
      <EmptyState v-if="!matches.length" title="Матчей нет" />
      <RouterLink v-for="m in matches" :key="m.id" class="row match-row" :to="`/matches/${m.id}`">
        <span class="match-side">
          <TeamCrest :src="names.logo(m.homeTeamId)" :name="m.homeTeamName || names.fullName(m.homeTeamId)" :size="22" />
          <span>{{ m.homeTeamName || names.fullName(m.homeTeamId) }}</span>
        </span>
        <span class="match-score">{{ cardScoreText(m) }}</span>
        <span class="match-side away">
          <span>{{ m.awayTeamName || names.fullName(m.awayTeamId) }}</span>
          <TeamCrest :src="names.logo(m.awayTeamId)" :name="m.awayTeamName || names.fullName(m.awayTeamId)" :size="22" />
        </span>
        <span class="match-meta">
          <MatchStageNote :match="m" />
          <span>{{ matchListWhen(m) }}</span>
          <span v-if="matchStateLabel(m.status)">{{ matchStateLabel(m.status) }}</span>
        </span>
      </RouterLink>
    </div>
    <PlayoffBracket
      v-else-if="tab === 'playoff'"
      role="tabpanel"
      :tournament-id="tournament.id"
      :rounds="playoff?.rounds ?? []"
      :meta="championMeta"
      :loading="playoffLoading"
      :error="playoffError"
      :editable="auth.canManageLeague && playoff?.competitionStage === 'PLAYOFF'"
      @saved="refreshAfterScheduleSave"
      @retry="loadPlayoff(String(tournament.id))"
      @show-table="setTab('table')"
    />
    <p v-if="error && !auth.canManageLeague" class="form-error">{{ error }}</p>
    <p v-if="ok && !auth.canManageLeague" class="form-ok">{{ ok }}</p>

    <ApplyTeamDialog
      v-if="applying && tournament"
      :tournament="tournament"
      @close="applying = false"
      @applied="onApplied"
    />

    <AdminOnly v-if="tournament && auth.canManageLeague" title="Для админа" open>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
      <h2>Настройки турнира</h2>
      <form class="stack" novalidate @submit.prevent="save">
        <label class="field">Название<input v-model="name" /></label>
        <label class="field">Описание<textarea v-model="description" rows="2" /></label>
        <label class="field">Сезон<input v-model.number="seasonYear" type="number" /></label>
        <label class="field">Старт<input v-model="startDate" type="date" /></label>
        <label class="field">Финиш<input v-model="endDate" type="date" /></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <label class="field">Статус
          <select v-model="status">
            <option value="DRAFT">Черновик</option>
            <option value="REGISTRATION">Набор</option>
            <option value="ACTIVE">Идёт</option>
            <option value="FINISHED">Завершён</option>
            <option value="CANCELLED">Отменён</option>
          </select>
        </label>
        <p v-if="playedWhileRecruiting" class="form-error">
          Матчи уже сыграны или идут, а статус всё ещё «Набор». Поставьте «Идёт» и сохраните. Завершённый турнир это само не переписывает.
        </p>
        <label class="field">Формат
          <select v-model="format">
            <option value="ROUND_ROBIN">Круговой турнир</option>
            <option value="CUP">Кубок / плей-офф</option>
            <option value="GROUPS_PLAYOFF">Группы + плей-офф</option>
            <option value="SWISS">Швейцарская система</option>
            <option value="DOUBLE_ELIMINATION">Double elimination</option>
          </select>
        </label>
        <label class="field">Регламент
          <textarea v-model="regulations" rows="5" />
        </label>
        <button class="btn secondary" type="submit" :disabled="pending">Сохранить</button>
      </form>

      <h2>Таблицы и группы</h2>
      <p class="muted">Сколько групп показать и какие команды в какой. Матчи между разными группами в зачёт не идут.</p>
      <label class="field">Количество таблиц
        <input
          type="number"
          min="0"
          max="16"
          :value="tableDraft.length"
          @input="setTableCount(Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <div v-for="(table, i) in tableDraft" :key="i" class="group-edit">
        <label class="field">Название
          <input v-model="table.name" maxlength="200" />
        </label>
        <div class="team-picks">
          <label v-for="team in assignableTeams" :key="team.teamId" class="pick">
            <input
              type="checkbox"
              :checked="table.teamIds.includes(team.teamId)"
              @change="toggleTeam(i, team.teamId)"
            />
            <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="18" />
            <span>{{ team.teamName }}</span>
          </label>
        </div>
      </div>
      <p v-if="assignableTeams.some((team) => teamInTable(team.teamId) < 0) && tableDraft.length" class="muted">
        Без таблицы:
        {{ assignableTeams.filter((team) => teamInTable(team.teamId) < 0).map((team) => team.teamName).join(', ') }}
      </p>
      <button class="btn secondary" type="button" :disabled="pending" @click="saveTables">Сохранить таблицы</button>

      <PlayoffAdmin
        :tournament-id="tournament.id"
        :tables="savedTables"
        :playoff="playoff"
        @changed="load"
      />

      <h2>Заявки</h2>
      <div v-for="team in listedTeams" :key="`admin-${team.id}`" class="row">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
        <div class="actions">
          <button v-if="showApproveButton(team.status)" class="btn" type="button" :disabled="pending" @click="approve(team.teamId)">{{ APPROVE_BUTTON_LABEL }}</button>
          <button class="btn danger" :disabled="pending" @click="exclude(team)">Убрать из турнира</button>
        </div>
      </div>
      <h2 v-if="withdrawnTeams.length">Снятые</h2>
      <div v-for="team in withdrawnTeams" :key="`withdrawn-${team.id}`" class="row withdrawn">
        <RouterLink class="club" :to="`/teams/${team.teamId}`">
          <TeamCrest :src="names.logo(team.teamId)" :name="team.teamName" :size="22" />
          {{ team.teamName }}
        </RouterLink>
        <StatusBadge :status="team.status" />
        <div class="actions">
          <button class="btn" type="button" :disabled="pending" @click="restore(team.teamId)">Вернуть</button>
        </div>
      </div>

      <h2>Календарь из Excel / CSV</h2>
      <p class="muted">Колонки: date, time, home, away. Необязательно: venue (зал), round (тур), group (группа). Повтор той же пары в том же туре и группе обновляет дату и зал. Снятую команду файл не возвращает: сначала нажмите «Вернуть».</p>
      <form class="stack" @submit.prevent="importCalendar">
        <input type="file" accept=".csv,.xlsx,.xls" @change="calendarFile = ($event.target as HTMLInputElement).files?.[0] || null" />
        <button class="btn secondary" type="submit" :disabled="pending || !calendarFile">Загрузить календарь</button>
      </form>

      <div class="head">
        <h2>Назначить матч</h2>
        <button class="btn" @click="showMatchForm = !showMatchForm">{{ showMatchForm ? 'Скрыть' : 'Форма матча' }}</button>
      </div>
      <CreateMatchForm v-if="showMatchForm" :tournament-id="tournament.id" @created="load" />
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="ok" class="form-ok">{{ ok }}</p>
    </AdminOnly>
  </section>
</template>

<style scoped>
.tour { min-width: 0; max-width: 100%; }
.tour-hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-end;
  background: var(--ink);
  color: #fff;
  border-radius: 22px;
  padding: 1.1rem 1.2rem 1.15rem;
}
.tour-copy { min-width: 0; }
.tour-copy a { color: var(--ice); font-weight: 800; text-decoration: none; }
.tour-hero h1 { color: #fff; font-size: clamp(1.45rem, 3vw, 2.1rem); margin: 0.25rem 0 0.45rem; text-transform: none; }
.tour-line { display: flex; flex-wrap: wrap; gap: 0.45rem 0.7rem; align-items: center; margin: 0; color: rgba(255,255,255,0.82); }
.stage { margin: 0.45rem 0 0; color: #fff; font-weight: 800; }
.figures { display: flex; gap: 1.1rem; align-items: flex-end; }
.figures b { display: block; color: #fff; font-size: 1.8rem; line-height: 1; font-variant-numeric: tabular-nums; }
.figures span { color: rgba(255,255,255,0.7); font-size: 0.75rem; font-weight: 700; }
.figures .ls { display: none; }
.fig-skel { width: 4.2rem; height: 2.6rem; border-radius: 10px; background: rgba(255,255,255,0.12); }
.fig-error { display: grid; gap: 0.4rem; justify-items: end; color: #fff; }
.tab-card { background: #fff; border-radius: 18px; padding: 0.2rem 0.6rem 0; min-width: 0; max-width: 100%; }
.table-layout { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 0.85rem; align-items: start; min-width: 0; }
.card-block { background: #fff; border-radius: 18px; padding: 0.85rem 0.9rem 1rem; min-width: 0; overflow: hidden; }
.table-side { display: grid; gap: 0.75rem; }
.card-block header { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; color: var(--navy); }
.card-block header button, .card-block header a { border: 0; background: transparent; color: var(--navy); font-weight: 800; font-size: 0.75rem; letter-spacing: 0.04em; text-transform: uppercase; cursor: pointer; }
.table-state { display: grid; justify-items: center; gap: 0.55rem; text-align: center; padding: 1rem 0; }
.table-skel { color: var(--muted); padding: 1rem 0; }
.legend { display: flex; align-items: center; gap: 0.4rem; color: var(--muted); font-size: 0.8rem; margin: 0.7rem 0 0; }
.legend i.ice { width: 0.7rem; height: 0.7rem; border-radius: 50%; background: var(--ice); }
.soon, .scorer { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.35rem; align-items: center; padding: 0.45rem 0; border-top: 1px solid var(--line); color: inherit; text-decoration: none; }
.soon b, .scorer b { display: block; color: var(--navy); }
.soon small, .scorer small { color: var(--muted); }
.soon em { font-style: normal; font-weight: 800; color: var(--navy); }
.scorer { grid-template-columns: auto auto minmax(0, 1fr) auto; }
.scorer .pos { color: var(--muted); font-weight: 800; }
.side-note { color: var(--muted); margin: 0.35rem 0; }
.match-state { display: grid; justify-items: center; gap: 0.6rem; padding: 1.2rem; background: #fff; border-radius: 18px; }
@media (max-width: 720px) {
  .tour-hero { flex-direction: column; align-items: stretch; border-radius: 18px; }
  .figures { justify-content: space-between; }
  .figures .lf { display: none; }
  .figures .ls { display: inline; }
  .table-layout { grid-template-columns: 1fr; }
}
.tour > * { min-width: 0; }
.crumbs { font-size: 0.8125rem; font-weight: 700; color: var(--muted); display: flex; align-items: center; gap: 0.35rem; min-width: 0; }
.crumbs a { color: var(--muted); }
.crumbs a:hover { color: var(--navy); }
.crumb-skel, .chip-skel, .title-skel, .meta-skel {
  display: block;
  background-color: #d3dde8;
  background-image: linear-gradient(100deg, #d3dde8 32%, #e8eef4 50%, #d3dde8 68%);
  background-size: 220% 100%;
  animation: tour-shimmer 1.4s ease-in-out infinite;
}
.crumb-skel { display: inline-block; width: 8rem; height: 0.8rem; border-radius: 6px; }
.th h1 { font-size: clamp(1.5rem, 3vw, 2.1rem); margin: 0.45rem 0 0.35rem; }
.title-pending { display: grid; gap: 0.55rem; justify-items: start; padding-top: 0.2rem; }
.chip-skel { width: 96px; height: 24px; border-radius: 999px; }
.title-skel { width: 320px; height: 28px; border-radius: 8px; }
.meta-skel { width: 420px; height: 14px; border-radius: 7px; }
.card-skel {
  display: block;
  border-radius: 10px;
  background: linear-gradient(90deg, #e8eef5 25%, #f4f7fb 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: tour-shimmer 1.4s infinite;
}
.tour-skel { display: grid; gap: 0.7rem; }
.card-skel { height: 7.5rem; }
.card-skel.short { width: 70%; height: 4.5rem; }
@keyframes tour-shimmer { to { background-position: -200% 0; } }
.tour-st {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.tour-st.ok { background: #dff3ec; color: #1f8f6b; }
.tour-st.soon { background: rgba(76, 180, 229, 0.16); color: #1b5f8c; }
.tour-st.done { background: #eef2f6; color: var(--muted); text-transform: none; letter-spacing: 0; }
.meta {
  display: flex;
  gap: 0.3rem 1rem;
  flex-wrap: wrap;
  margin: 0;
  font-size: 0.875rem;
  color: var(--muted);
  font-weight: 600;
}
.meta span + span::before { content: "·"; margin-right: 1rem; color: var(--line-strong); }
.lede { margin: 0.45rem 0 0; color: var(--muted); }
.ttabs {
  display: flex;
  gap: 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--line-strong);
  scrollbar-width: none;
  min-width: 0;
  max-width: 100%;
}
.ttabs::-webkit-scrollbar { display: none; }
.ttabs button {
  flex: 0 0 auto;
  padding: 0.8rem 1rem;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--muted);
  font-weight: 800;
  font-size: 0.8125rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  display: flex;
  gap: 0.4rem;
  align-items: center;
  cursor: pointer;
}
.ttabs button.on { color: var(--navy); border-bottom-color: var(--ice); }
.ttabs i {
  font-style: normal;
  background: #e3ebf3;
  color: var(--muted);
  border-radius: 999px;
  padding: 0 0.45rem;
  font-size: 0.75rem;
  line-height: 1.5;
}
.ttabs .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); }
h2 { font-size: 1.2rem; margin-bottom: 0.45rem; }
.head { display: flex; justify-content: space-between; gap: 1rem; align-items: center; flex-wrap: wrap; }
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 0.15rem;
  border-bottom: 1px solid var(--line);
  color: var(--text-strong);
  text-decoration: none;
}
.row:hover { color: var(--accent); }
.row.withdrawn {
  color: #8b97a6;
  background: #f3f5f8;
}
.row.withdrawn .club,
.row.withdrawn:hover,
.row.withdrawn:hover .club {
  color: #8b97a6;
}
.row.match-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto;
  align-items: center;
}
.match-side {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}
.match-side.away { justify-content: flex-end; }
.match-score {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  white-space: nowrap;
}
.match-meta {
  display: grid;
  justify-items: end;
  color: var(--muted);
  font-size: 0.82rem;
  white-space: nowrap;
}
.rules-blank { padding: 1.8rem 1.4rem; }
.rules-empty { text-align: center; color: var(--muted); }
.rules-empty strong {
  display: block;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: 1.2rem;
  margin-bottom: 0.35rem;
}
.rules-empty p { margin: 0; }
.rules-body { white-space: pre-wrap; }
@media (prefers-reduced-motion: reduce) {
  .crumb-skel, .chip-skel, .title-skel, .meta-skel, .card-skel { animation: none; }
  .crumb-skel, .chip-skel, .title-skel, .meta-skel {
    background: #d3dde8;
    background-image: none;
  }
}
@media (max-width: 720px) {
  .th h1 { font-size: 1.5rem; }
  .title-skel { width: 70%; height: 28px; }
  .meta-skel { width: 90%; }
  .ttabs button { padding: 0.75rem 0.8rem; }
  .meta { gap: 0.15rem 1rem; }
  .meta span + span::before { display: none; }
  .ttabs { flex-wrap: wrap; overflow: visible; }
  .row.match-row {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  }
  .match-meta {
    grid-column: 1 / -1;
    justify-items: start;
    white-space: normal;
  }
}
.actions { display: flex; gap: 0.45rem; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
.group-edit {
  display: grid;
  gap: 0.65rem;
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--line);
}
.team-picks {
  display: grid;
  gap: 0.35rem;
}
.pick {
  display: grid;
  grid-template-columns: auto 18px 1fr;
  gap: 0.45rem;
  align-items: center;
  color: var(--text-strong);
}
</style>
