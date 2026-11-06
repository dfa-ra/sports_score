<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventLabel, isOwnGoal, labelOf, periodLabel, playerTag, statusLabel } from '../lib/format'
import { registeredName } from '../lib/playerTitle'
import { buildPeriodBlocks, compareMatchEvents, eventMinute, eventOnHomeSide, matchCardWhen, penaltyPair, protocolMinute as protocolMinuteOf, protocolMinuteLimit, publicScore, unplayedCardScore } from '../lib/match'
import { apiError } from '../lib/errors'
import { setPageMeta } from '../lib/pageMeta'
import { loadAllReferees, refereeTitle } from '../lib/referees'
import { cardScoreText } from '../lib/calendarFeed'
import { aboutStage, classifyFormPayload, formMarks, keepFormPayload, refereeNames, venueLabel } from '../lib/matchPresentation'
import NotFoundView from './NotFoundView.vue'
import { useMatchClock } from '../lib/useMatchClock'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import AnalystAssignForm from '../components/AnalystAssignForm.vue'
import CopyChip from '../components/CopyChip.vue'
import FutsalBoard from '../components/FutsalBoard.vue'
import SquadPlayerSelect from '../components/SquadPlayerSelect.vue'
import MatchGoalAlerts from '../components/MatchGoalAlerts.vue'
import MatchLineupBoard from '../components/MatchLineupBoard.vue'
import MatchShareButton from '../components/MatchShareButton.vue'
import PenaltyShootoutAdmin from '../components/PenaltyShootoutAdmin.vue'
import TeamCrest from '../components/TeamCrest.vue'
import MatchAnalystStats from '../components/MatchAnalystStats.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const enteringGoalIds = ref<Set<string>>(new Set())
let knownGoalIds = new Set<string>()
let goalMatchId = ''
const referees = ref<any[]>([])
const lineups = ref<any>(null)
const homeForm = ref<any[]>([])
const awayForm = ref<any[]>([])
const formError = ref(false)
const formReady = ref(false)
const tournament = ref<any>(null)
const loading = ref(true)
const me = ref<any>(null)
const users = ref<any[]>([])
const refereeId = ref('')
const protocolType = ref<'GOAL' | 'YELLOW_CARD' | 'RED_CARD'>('GOAL')
const protocolTeamId = ref('')
const protocolPlayerId = ref('')
const protocolMinute = ref(1)
const protocolOwnGoal = ref(false)
const homeRoster = ref<any[]>([])
const awayRoster = ref<any[]>([])
const minuteDrafts = ref<Record<string, number | null>>({})
const playerDrafts = ref<Record<string, string>>({})
const assistDrafts = ref<Record<string, string>>({})
const ownGoalDrafts = ref<Record<string, boolean>>({})
const motmPlayerId = ref('')
const homeKeeperId = ref('')
const awayKeeperId = ref('')
function crestForViewport() {
  return window.innerWidth <= 719 ? 72 : 96
}

const crestSize = ref(crestForViewport())
const homeStarTap = ref(0)
const awayStarTap = ref(0)
type MatchTab = 'overview' | 'stats' | 'lineups' | 'protocol'
const matchTabs: MatchTab[] = ['overview', 'stats', 'lineups', 'protocol']
const tab = computed<MatchTab>(() => {
  const raw = String(route.query.tab || '')
  return matchTabs.includes(raw as MatchTab) ? raw as MatchTab : 'overview'
})
function setTab(next: MatchTab) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (key === 'tab') continue
    if (typeof value === 'string' && value) query[key] = value
  }
  if (next !== 'overview') query.tab = next
  if (String(route.query.tab || '') === (query.tab || '')) return
  void router.replace({ query })
}
const connected = ref(false)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const missing = ref(false)
const teams = useTeamDirectory()
const fav = useFavorites()
const { minute, expired } = useMatchClock(match)
let client: Client | null = null
let pollTimer = 0

const timeline = computed(() =>
  [...events.value]
    .filter((event) => !event.voided)
    .sort(compareMatchEvents),
)
const homeMarks = computed(() => formMarks(homeForm.value, match.value?.homeTeamId, match.value?.id))
const awayMarks = computed(() => formMarks(awayForm.value, match.value?.awayTeamId, match.value?.id))
const publicReferees = computed(() => refereeNames(referees.value))
const stageText = computed(() => aboutStage(match.value, tournament.value))
const placeText = computed(() => venueLabel(match.value?.venue))
const heroScore = computed(() => match.value ? cardScoreText(match.value) : '')
const tournamentTitle = computed(() => tournament.value?.name || match.value?.tournamentName || 'Турнир')

const protocolMinuteMax = computed(() =>
  protocolMinuteLimit(match.value?.periodLengthSeconds, match.value?.periodCount),
)
const periodBlocks = computed(() => {
  if (!match.value) return []
  return buildPeriodBlocks(events.value, match.value).map((block) => ({
    ...block,
    label: periodLabel(block.period, match.value.sportCode, match.value.periodCount),
  }))
})

function shownMinute(ev: { eventType?: string | null; gameTime?: number | null; period?: number | null }) {
  return protocolMinuteOf(ev, match.value?.periodLengthSeconds)
}

function playersOf(teamId: string) {
  if (!match.value || !teamId) return []
  const home = teamId === match.value.homeTeamId
  const roster = home ? homeRoster.value : awayRoster.value
  const side = home ? lineups.value?.home : lineups.value?.away
  const squad = [...(side?.starters ?? []), ...(side?.bench ?? [])]
  const rows = squad.length ? squad : roster
  const seen = new Set<string>()
  return rows.filter((player) => {
    if (!player?.playerId || seen.has(player.playerId)) return false
    seen.add(player.playerId)
    return true
  }).map((player) => ({ ...player, teamId }))
}

const protocolPlayers = computed(() => playersOf(protocolTeamId.value))

const goalPlayerOptions = computed(() => {
  if (!match.value) return []
  const rows = [...playersOf(match.value.homeTeamId), ...playersOf(match.value.awayTeamId)]
  const seen = new Set<string>()
  return rows.filter((player) => {
    if (seen.has(player.playerId)) return false
    seen.add(player.playerId)
    return true
  }).map((player) => ({
    playerId: player.playerId,
    teamId: player.teamId,
    teamName: teams.fullName(player.teamId),
    label: protocolPlayerLabel(player),
  }))
})

function assistOptions(eventId: string) {
  const scorer = playerDrafts.value[eventId]
  return goalPlayerOptions.value.filter((player) => player.playerId !== scorer)
}

function protocolPlayerLabel(player: any) {
  return playerTag(registeredName(player), player.jerseyNumber)
}

const editableEvents = computed(() =>
  [...events.value]
    .filter((event) => !event.voided && event.eventType !== 'PERIOD_START' && event.eventType !== 'PERIOD_END')
    .sort(compareMatchEvents),
)

function syncDrafts() {
  const minutes: Record<string, number | null> = {}
  const players: Record<string, string> = {}
  const assists: Record<string, string> = {}
  const ownGoals: Record<string, boolean> = {}
  for (const event of events.value) {
    minutes[event.id] = eventMinute(event.gameTime, event.period, match.value?.periodLengthSeconds)
    players[event.id] = event.playerId || ''
    const ownGoal = isOwnGoal(event)
    ownGoals[event.id] = ownGoal
    assists[event.id] = !ownGoal && event.secondaryPlayerId && event.secondaryPlayerId !== event.playerId
      ? event.secondaryPlayerId
      : ''
  }
  minuteDrafts.value = minutes
  playerDrafts.value = players
  assistDrafts.value = assists
  ownGoalDrafts.value = ownGoals
}

function onOwnGoalToggle(eventId: string, ownGoal: boolean) {
  ownGoalDrafts.value[eventId] = ownGoal
  if (ownGoal) assistDrafts.value[eventId] = ''
}

function onOwnGoalInput(eventId: string, event: Event) {
  const input = event.target as HTMLInputElement | null
  onOwnGoalToggle(eventId, !!input?.checked)
}

function onScorerChange(eventId: string, playerId: string) {
  if (assistDrafts.value[eventId] === playerId) assistDrafts.value[eventId] = ''
}

function applyCrest() {
  crestSize.value = crestForViewport()
}

watch(events, (list) => {
  const matchId = String(match.value?.id || '')
  if (!matchId) return
  const ids = list
    .filter((event) => !event?.voided && (event.eventType === 'GOAL' || event.eventType === 'OWN_GOAL'))
    .map((event) => String(event.id))
  if (goalMatchId !== matchId) {
    goalMatchId = matchId
    knownGoalIds = new Set(ids)
    enteringGoalIds.value = new Set()
    return
  }
  const fresh = ids.filter((id) => !knownGoalIds.has(id))
  knownGoalIds = new Set(ids)
  if (!fresh.length) return
  const entering = new Set(enteringGoalIds.value)
  for (const id of fresh) entering.add(id)
  enteringGoalIds.value = entering
  window.setTimeout(() => {
    const next = new Set(enteringGoalIds.value)
    for (const id of fresh) next.delete(id)
    enteringGoalIds.value = next
  }, 520)
})

function freshGoal(event: { id?: string | null; eventType?: string | null }) {
  if (event.eventType !== 'GOAL' && event.eventType !== 'OWN_GOAL') return false
  return enteringGoalIds.value.has(String(event.id))
}

function protocolSide(ev: { teamId?: string | null; eventType?: string | null; metadata?: { ownGoal?: boolean } | null }) {
  if (!ev.teamId || !match.value) return 'plain'
  return eventOnHomeSide(ev, match.value.homeTeamId) ? 'home' : 'away'
}

function protocolExtra(ev: {
  eventType?: string | null
  metadata?: { ownGoal?: boolean } | null
  secondaryPlayerName?: string | null
  secondaryPlayerFirstName?: string | null
  secondaryPlayerLastName?: string | null
  secondaryPlayerJersey?: number | null
}) {
  if (isOwnGoal(ev)) return ''
  const second = playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey)
  if (ev.eventType === 'GOAL' && second) return `· пас ${second}`
  if (ev.eventType === 'SUBSTITUTION' && second) return `→ ${second}`
  return ''
}

function tapTeamStar(side: 'home' | 'away', teamId: string) {
  fav.toggleTeam(teamId)
  if (side === 'home') homeStarTap.value += 1
  else awayStarTap.value += 1
}

watch(protocolPlayers, (players) => {
  if (!players.some((player) => player.playerId === protocolPlayerId.value)) {
    protocolPlayerId.value = players[0]?.playerId || ''
  }
})

const assignedReferee = computed(() => {
  const id = auth.user?.id
  if (!id) return false
  return referees.value.some((row) => row.refereeId === id)
})
const canUsePad = computed(() => auth.canManageLeague || assignedReferee.value)
const shootoutScore = computed(() => (match.value && penaltyPair(match.value) ? publicScore(match.value) : ''))
const pendingScore = computed(() => unplayedCardScore(match.value?.status))

function isCaptainOf(teamId?: string) {
  if (!me.value?.id || !teamId) return false
  const side = teamId === match.value?.homeTeamId ? lineups.value?.home : lineups.value?.away
  return side?.captainId === me.value.id
}

function typedMinute(eventId: string) {
  const value = minuteDrafts.value[eventId]
  if (value == null || Number.isNaN(Number(value))) return null
  return Number(value)
}

let pageTicket = 0
async function load() {
  const ticket = ++pageTicket
  const id = String(route.params.id || '')
  missing.value = false
  if (!match.value) loading.value = true
  try {
  await teams.load()
  if (ticket !== pageTicket) return
  const [m, e, r, l] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
    api.get(`/matches/${id}/referees`),
    api.get(`/matches/${id}/lineups`),
  ])
  if (ticket !== pageTicket || String(route.params.id || '') !== id) return
  match.value = m.data
  error.value = ''
  const homeName = teams.fullName(m.data.homeTeamId)
  const awayName = teams.fullName(m.data.awayTeamId)
  const publish = () => setPageMeta({
    title: `${homeName} — ${awayName}`,
    description: `Матч ${homeName} — ${awayName}. Счёт, протокол и составы.`,
    path: `/matches/${id}`,
  })
  publish()
  void nextTick(publish)
  motmPlayerId.value = m.data.playerOfTheMatch?.playerId || ''
  homeKeeperId.value = m.data.homeGoalkeeper?.playerId || ''
  awayKeeperId.value = m.data.awayGoalkeeper?.playerId || ''
  events.value = e.data
  syncDrafts()
  referees.value = r.data
  lineups.value = l.data
  if (!protocolTeamId.value) protocolTeamId.value = m.data.homeTeamId
  homeRoster.value = []
  awayRoster.value = []
  if (auth.canManageLeague) {
    try {
      const [homeMembers, awayMembers] = await Promise.all([
        api.get(`/teams/${m.data.homeTeamId}/members`),
        api.get(`/teams/${m.data.awayTeamId}/members`),
      ])
      homeRoster.value = homeMembers.data ?? []
      awayRoster.value = awayMembers.data ?? []
    } catch {
      homeRoster.value = []
      awayRoster.value = []
    }
  }
  await loadForms(m.data.homeTeamId, m.data.awayTeamId)
  if (ticket !== pageTicket) return
  try {
    const { data } = await api.get(`/tournaments/${m.data.tournamentId}`)
    tournament.value = data
  } catch {
    tournament.value = null
  }
  if (auth.isAuthenticated) {
    try {
      const { data } = await api.get('/players/me')
      me.value = data
    } catch {
      me.value = null
    }
  }
  if (auth.canManageLeague) {
    users.value = await loadAllReferees()
    const stillListed = users.value.some((user) => user.id === refereeId.value)
    if (!refereeId.value || !stillListed) {
      const assignedId = referees.value.find((row) =>
        users.value.some((user) => user.id === row.refereeId),
      )?.refereeId
      refereeId.value = assignedId || users.value[0]?.id || ''
    }
  }
  if (ticket !== pageTicket) return
  error.value = ''
  } catch (e: any) {
    if (ticket !== pageTicket) return
    const status = e?.response?.status
    if (status === 400 || status === 404) {
      match.value = null
      missing.value = true
      error.value = ''
      return
    }
    if (!match.value) error.value = 'Матч не загрузился'
  } finally {
    if (ticket === pageTicket) loading.value = false
  }
}

let formTicket = 0
async function loadForms(homeTeamId?: string, awayTeamId?: string) {
  const ticket = ++formTicket
  const matchId = String(route.params.id || '')
  const stillThisMatch = () => ticket === formTicket && matchId === String(route.params.id || '')
  if (!homeTeamId || !awayTeamId) {
    const empty = keepFormPayload(ticket, formTicket, matchId, String(route.params.id || ''), [] as any[])
    if (!empty) return
    homeForm.value = []
    awayForm.value = []
    formError.value = false
    formReady.value = true
    return
  }
  try {
    const [hf, af] = await Promise.all([
      api.get(`/teams/${homeTeamId}/form`, { params: { limit: 5 } }),
      api.get(`/teams/${awayTeamId}/form`, { params: { limit: 5 } }),
    ])
    const homeRaw = keepFormPayload(ticket, formTicket, matchId, String(route.params.id || ''), hf.data)
    const awayRaw = keepFormPayload(ticket, formTicket, matchId, String(route.params.id || ''), af.data)
    if (homeRaw == null || awayRaw == null || !stillThisMatch()) return
    if (match.value && (String(match.value.homeTeamId) !== String(homeTeamId) || String(match.value.awayTeamId) !== String(awayTeamId))) return
    const home = classifyFormPayload(homeRaw, homeTeamId, matchId)
    const away = classifyFormPayload(awayRaw, awayTeamId, matchId)
    if (!stillThisMatch()) return
    if (!home.ok || !away.ok) {
      formError.value = true
      return
    }
    homeForm.value = home.rows
    awayForm.value = away.rows
    formError.value = false
    formReady.value = true
  } catch {
    if (!stillThisMatch()) return
    formError.value = true
  }
}

function markClass(mark: string) {
  if (mark === 'В') return 'win'
  if (mark === 'Н') return 'draw'
  return 'loss'
}

function blockScore(value?: string | null) {
  if (!value) return ''
  const parts = String(value).split('-')
  if (parts.length !== 2) return value
  return `${parts[0]} : ${parts[1]}`
}

function eventSignature(list: any[]) {
  return list.map((ev) => `${ev.id}:${ev.voided}:${ev.gameTime}:${ev.playerId ?? ''}:${ev.secondaryPlayerId ?? ''}`).join('|')
}

async function refreshLive() {
  if (pending.value || !route.params.id) return
  const id = String(route.params.id || '')
  const [m, e] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
  ])
  if (String(route.params.id || '') !== id) return
  match.value = { ...match.value, ...m.data }
  const next = e.data ?? []
  if (eventSignature(next) !== eventSignature(events.value)) {
    events.value = next
    syncDrafts()
  }
  error.value = ''
}

function mergeLive(live: any) {
  match.value = {
    ...match.value,
    status: live.status,
    homeScore: live.homeScore,
    awayScore: live.awayScore,
    gameTimeSeconds: live.gameTimeSeconds,
    period: live.period,
    periodCount: live.periodCount ?? match.value.periodCount,
    periodLengthSeconds: live.periodLengthSeconds ?? match.value.periodLengthSeconds,
    clockRunningSince: live.clockRunningSince,
    sportCode: live.sportCode ?? match.value.sportCode,
  }
  if (live.lastEvent) {
    events.value = [...events.value.filter((x: any) => x.id !== live.lastEvent.id), live.lastEvent]
    syncDrafts()
  }
}

const refereeNow = computed(() => {
  if (!referees.value.length) return 'Сейчас никто не назначен.'
  const lines = referees.value
    .map((row) => refereeTitle({
      id: row.refereeId,
      email: row.email || '',
      firstName: row.firstName,
      lastName: row.lastName,
    }))
    .filter(Boolean)
  return lines.length ? `Сейчас: ${lines.join('; ')}` : 'Сейчас никто не назначен.'
})

async function assignReferee() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/matches/${match.value.id}/referees`, { refereeId: refereeId.value })
    ok.value = 'Судья назначен. Пульт уже ждёт.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function addProtocolEvent() {
  error.value = ''
  ok.value = ''
  if (!protocolTeamId.value || !protocolPlayerId.value) {
    error.value = 'Выберите команду и игрока.'
    return
  }
  pending.value = true
  try {
    await api.post(`/admin/matches/${match.value.id}/events`, {
      eventType: protocolType.value,
      teamId: protocolTeamId.value,
      playerId: protocolPlayerId.value,
      minute: Number(protocolMinute.value),
      ownGoal: protocolType.value === 'GOAL' ? protocolOwnGoal.value : false,
    })
    ok.value = 'Событие записано в протокол.'
    await load()
  } catch (e: any) {
    error.value = apiError(e, 'Событие не записалось.')
  } finally {
    pending.value = false
  }
}

async function saveGoal(event: any) {
  error.value = ''
  ok.value = ''
  const playerId = playerDrafts.value[event.id]
  if (!playerId) {
    error.value = 'Выберите забившего.'
    return
  }
  const ownGoal = !!ownGoalDrafts.value[event.id]
  const minute = typedMinute(event.id)
  if (minute == null) {
    error.value = 'Укажите минуту.'
    return
  }
  pending.value = true
  try {
    await api.patch(`/admin/matches/${match.value.id}/events/${event.id}`, {
      minute,
      playerId,
      secondaryPlayerId: ownGoal ? null : (assistDrafts.value[event.id] || null),
      updatePlayers: true,
      ownGoal,
    })
    ok.value = 'Гол обновлён.'
    await load()
  } catch (e: any) {
    error.value = apiError(e, 'Гол не сохранился.')
  } finally {
    pending.value = false
  }
}

async function saveMinute(event: any) {
  error.value = ''
  ok.value = ''
  const minute = typedMinute(event.id)
  if (minute == null) {
    error.value = 'Укажите минуту.'
    return
  }
  pending.value = true
  try {
    await api.patch(`/admin/matches/${match.value.id}/events/${event.id}`, {
      minute,
    })
    ok.value = 'Минута обновлена.'
    await load()
  } catch (e: any) {
    error.value = apiError(e, 'Минута не сохранилась.')
  } finally {
    pending.value = false
  }
}

async function voidProtocolEvent(event: any) {
  const label = labelOf(eventLabel, event.eventType)
  if (!confirm(`Убрать «${label}» из протокола? Счёт пересчитается по оставшимся голам.`)) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.post(`/admin/matches/${match.value.id}/events/${event.id}/void`)
    ok.value = 'Событие убрано из протокола.'
    await load()
  } catch (e: any) {
    error.value = apiError(e, 'Событие не убралось.')
  } finally {
    pending.value = false
  }
}

async function deleteMatch() {
  const home = teams.fullName(match.value.homeTeamId)
  const away = teams.fullName(match.value.awayTeamId)
  if (!confirm(`Удалить матч ${home} — ${away}? Протокол, составы и назначения тоже сотрутся.`)) return
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.delete(`/matches/${match.value.id}`)
    await router.push('/admin')
  } catch (e: any) {
    error.value = apiError(e, 'Матч не удалился.')
    pending.value = false
  }
}

async function saveKeepers() {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    await api.put(`/admin/matches/${match.value.id}/goalkeeper`, {
      teamId: match.value.homeTeamId,
      playerId: homeKeeperId.value || null,
    })
    await api.put(`/admin/matches/${match.value.id}/goalkeeper`, {
      teamId: match.value.awayTeamId,
      playerId: awayKeeperId.value || null,
    })
    ok.value = 'Вратари сохранены.'
    await load()
  } catch (e: any) {
    error.value = apiError(e, 'Вратари не сохранились.')
  } finally {
    pending.value = false
  }
}

async function savePlayerOfTheMatch(playerId = motmPlayerId.value) {
  error.value = ''
  ok.value = ''
  pending.value = true
  try {
    const { data } = await api.put(`/admin/matches/${match.value.id}/player-of-the-match`, {
      playerId: playerId || null,
    })
    match.value = data
    motmPlayerId.value = data.playerOfTheMatch?.playerId || ''
    ok.value = data.playerOfTheMatch?.playerId ? 'Игрок матча выбран.' : 'Игрок матча снят.'
  } catch (e: any) {
    error.value = apiError(e, 'Игрок матча не сохранился.')
  } finally {
    pending.value = false
  }
}

async function clearPlayerOfTheMatch() {
  motmPlayerId.value = ''
  await savePlayerOfTheMatch('')
}

async function saveLineup(payload: { teamId: string; starterPlayerIds: string[]; benchPlayerIds: string[] }) {
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.put(`/matches/${match.value.id}/lineups`, payload)
    lineups.value = data
    ok.value = 'Стартовый состав записан. Как заявка на стипендию, только спортивнее.'
  } catch (e: any) {
    error.value = apiError(e, 'Состав не записался. Нужен капитан этой команды.')
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  applyCrest()
  window.addEventListener('resize', applyCrest)
  await load()
  client = new Client({
    webSocketFactory: () => new SockJS('/ws') as any,
    connectHeaders: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
    onConnect: () => {
      connected.value = true
      client?.subscribe(`/topic/matches/${route.params.id}`, (message) => {
        const live = JSON.parse(message.body)
        mergeLive(live)
        if (live.type === 'PERIOD_CHANGED' || live.type === 'MATCH_STARTED') {
          api.get(`/matches/${route.params.id}/events`).then(({ data }) => { events.value = data })
        }
      })
    },
    onDisconnect: () => { connected.value = false },
  })
  client.activate()
  pollTimer = window.setInterval(() => {
    refreshLive().catch((reason: unknown) => {
      if (!match.value) error.value = apiError(reason, 'Матч не обновился.')
    })
  }, 4_000)
})

watch(() => route.fullPath, () => {
  if (!match.value || missing.value) return
  const id = String(route.params.id || '')
  const homeName = teams.fullName(match.value.homeTeamId, match.value.homeTeamName || 'Команда')
  const awayName = teams.fullName(match.value.awayTeamId, match.value.awayTeamName || 'Команда')
  void nextTick(() => {
    if (!match.value || String(route.params.id || '') !== id) return
    setPageMeta({
      title: `${homeName} — ${awayName}`,
      description: `Матч ${homeName} — ${awayName}. Счёт, протокол и составы.`,
      path: `/matches/${id}`,
    })
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', applyCrest)
  if (pollTimer) window.clearInterval(pollTimer)
  client?.deactivate()
})
</script>

<template>
  <MatchGoalAlerts headless />
  <section v-if="loading" class="match-state" role="status">
    <p>Загрузка матча…</p>
    <span class="bar"></span>
    <span class="bar short"></span>
  </section>
  <section v-else-if="match" class="stack match-page">
    <header class="pitch">
      <div class="pitch-top">
        <RouterLink v-if="match.tournamentId" :to="`/tournaments/${match.tournamentId}`">‹ {{ tournamentTitle }}</RouterLink>
        <span v-else>‹ {{ tournamentTitle }}</span>
        <i class="pill" :class="(match.status || '').toLowerCase()">{{ statusLabel[match.status] || match.status }}</i>
      </div>
      <div class="pitch-score">
        <div class="pitch-side">
          <button class="fav-star" type="button" :class="{ on: fav.hasTeam(match.homeTeamId) }" :aria-label="teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')" @click="tapTeamStar('home', match.homeTeamId)">★</button>
          <RouterLink class="pitch-who" :to="`/teams/${match.homeTeamId}`">
            <TeamCrest :src="teams.logo(match.homeTeamId)" :name="teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')" :size="72" />
            <strong>{{ teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда') }}</strong>
          </RouterLink>
        </div>
        <div class="nums">
          <b>{{ heroScore }}</b>
          <p v-if="shootoutScore && !pendingScore" class="pens">{{ shootoutScore }}</p>
          <p v-if="match.status === 'LIVE' || match.status === 'PAUSED'" class="clock" :class="{ expired }">
            <template v-if="minute != null">{{ minute }}' · </template>{{ periodLabel(match.period, match.sportCode, match.periodCount) }}
          </p>
        </div>
        <div class="pitch-side away">
          <RouterLink class="pitch-who" :to="`/teams/${match.awayTeamId}`">
            <TeamCrest :src="teams.logo(match.awayTeamId)" :name="teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда')" :size="72" />
            <strong>{{ teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда') }}</strong>
          </RouterLink>
          <button class="fav-star" type="button" :class="{ on: fav.hasTeam(match.awayTeamId) }" @click="tapTeamStar('away', match.awayTeamId)">★</button>
        </div>
      </div>
      <p class="pitch-meta">{{ matchCardWhen(match) }} · {{ placeText }}</p>
      <MatchShareButton
        :home-name="teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')"
        :away-name="teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда')"
        :home-score="match.homeScore"
        :away-score="match.awayScore"
        :score-text="heroScore"
      />
    </header>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа" :open="match.status === 'FINISHED'">
      <CopyChip :value="String(match.id)" label="Скопировать id матча" />
      <form v-if="match.status === 'FINISHED'" class="stack motm-form" @submit.prevent="savePlayerOfTheMatch()">
        <h2>Игрок матча</h2>
        <p class="muted">Один на матч. Новый выбор заменяет предыдущего. Список — из составов, а если состав не подан, из заявки команды.</p>
        <label class="field">Игрок
          <select v-model="motmPlayerId">
            <option value="">Не выбран</option>
            <option v-for="player in goalPlayerOptions" :key="player.playerId" :value="player.playerId">
              {{ player.label }} · {{ player.teamName }}
            </option>
          </select>
        </label>
        <div class="motm-actions">
          <button class="btn" type="submit" :disabled="pending">Сохранить</button>
          <button
            v-if="match.playerOfTheMatch?.playerId"
            class="btn secondary"
            type="button"
            :disabled="pending"
            @click="clearPlayerOfTheMatch"
          >Снять</button>
        </div>
      </form>
      <form class="stack motm-form" @submit.prevent="saveKeepers">
        <h2>Вратари</h2>
        <p class="muted">Кто стоял в воротах. Один на команду, новый выбор заменяет предыдущий. Если оставить «Как в составе», сухой матч засчитается вратарю из заявленного состава.</p>
        <label class="field">{{ teams.fullName(match.homeTeamId) }}
          <select v-model="homeKeeperId">
            <option value="">Как в составе</option>
            <option v-for="player in playersOf(match.homeTeamId)" :key="player.playerId" :value="player.playerId">
              {{ protocolPlayerLabel(player) }}
            </option>
          </select>
        </label>
        <label class="field">{{ teams.fullName(match.awayTeamId) }}
          <select v-model="awayKeeperId">
            <option value="">Как в составе</option>
            <option v-for="player in playersOf(match.awayTeamId)" :key="player.playerId" :value="player.playerId">
              {{ protocolPlayerLabel(player) }}
            </option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending">Сохранить вратарей</button>
      </form>
      <PenaltyShootoutAdmin
        v-if="match.bracketRound && match.status === 'FINISHED' && match.homeScore === match.awayScore"
        :match-id="String(match.id)"
        :home-team-id="match.homeTeamId"
        :away-team-id="match.awayTeamId"
        :home-label="teams.fullName(match.homeTeamId)"
        :away-label="teams.fullName(match.awayTeamId)"
        :home-score="match.homeScore"
        :away-score="match.awayScore"
        @saved="load"
      />
      <h2>Протокол</h2>
      <p class="muted">Гол, жёлтая или красная. У гола можно отметить автогол: очко уходит сопернику, в бомбардиры этот гол не идёт, передачи нет. Забившего и ассистента можно сменить. Минута — от начала матча, как её увидит зритель: во втором тайме 2×15 это 16' и дальше, не время на табло. Зрители видят счёт без убранных голов.</p>
      <form class="stack protocol-form" @submit.prevent="addProtocolEvent">
        <label class="field">Событие
          <select v-model="protocolType">
            <option value="GOAL">Гол</option>
            <option value="YELLOW_CARD">Жёлтая</option>
            <option value="RED_CARD">Красная</option>
          </select>
        </label>
        <label class="field">Команда
          <select v-model="protocolTeamId">
            <option :value="match.homeTeamId">{{ teams.fullName(match.homeTeamId) }}</option>
            <option :value="match.awayTeamId">{{ teams.fullName(match.awayTeamId) }}</option>
          </select>
        </label>
        <label class="field">Игрок
          <select v-model="protocolPlayerId" required>
            <option v-if="!protocolPlayers.length" value="" disabled>В заявке никого нет</option>
            <option v-for="player in protocolPlayers" :key="player.playerId" :value="player.playerId">
              {{ protocolPlayerLabel(player) }}
            </option>
          </select>
        </label>
        <label class="field">Минута от начала
          <input v-model.number="protocolMinute" type="number" min="0" :max="protocolMinuteMax" required />
        </label>
        <label v-if="protocolType === 'GOAL'" class="check-line">
          <input v-model="protocolOwnGoal" type="checkbox" />
          Автогол
        </label>
        <button class="btn" type="submit" :disabled="pending || !protocolPlayers.length">Добавить в протокол</button>
      </form>
      <div v-for="ev in editableEvents" :key="ev.id" class="proto-row">
        <span class="proto-kind">
          <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
          <small v-if="ev.eventType !== 'GOAL'" class="muted">{{ registeredName(ev) || 'без игрока' }}</small>
        </span>
        <template v-if="ev.eventType === 'GOAL'">
          <span class="field player">Забивший
            <SquadPlayerSelect
              v-model="playerDrafts[ev.id]"
              :options="goalPlayerOptions"
              :fallback="registeredName(ev) || 'Игрок'"
              :disabled="pending"
              @update:model-value="onScorerChange(ev.id, $event)"
            />
          </span>
          <label class="check-line">
            <input
              type="checkbox"
              :checked="!!ownGoalDrafts[ev.id]"
              :disabled="pending"
              @change="onOwnGoalInput(ev.id, $event)"
            />
            Автогол
          </label>
          <span v-if="!ownGoalDrafts[ev.id]" class="field player">Ассистент
            <SquadPlayerSelect
              v-model="assistDrafts[ev.id]"
              :options="assistOptions(ev.id)"
              empty-label="без ассистента"
              :fallback="registeredName(ev, 'secondary')"
              :disabled="pending"
            />
          </span>
        </template>
        <label class="field minute">Минута от начала
          <input v-model.number="minuteDrafts[ev.id]" type="number" min="0" :max="protocolMinuteMax" />
        </label>
        <button
          v-if="ev.eventType === 'GOAL'"
          class="btn secondary"
          type="button"
          title="Сохранить забившего, ассистента и минуту"
          :disabled="pending"
          @click="saveGoal(ev)"
        >Сохранить</button>
        <button
          v-else
          class="btn secondary"
          type="button"
          :disabled="pending"
          @click="saveMinute(ev)"
        >Сохранить минуту</button>
        <button class="btn danger" type="button" :disabled="pending" @click="voidProtocolEvent(ev)">Убрать</button>
      </div>
      <p v-if="!editableEvents.length" class="muted">В протоколе пока нет событий.</p>
      <button class="btn danger" type="button" :disabled="pending" @click="deleteMatch">Удалить матч</button>
      <AnalystAssignForm
        :match-id="String(match.id)"
        :home-label="teams.fullName(match.homeTeamId, 'Хозяева')"
        :away-label="teams.fullName(match.awayTeamId, 'Гости')"
      />
      <form class="panel stack assign-card" @submit.prevent="assignReferee">
        <h2>Судья</h2>
        <p class="muted">{{ refereeNow }}</p>
        <label class="field">Назначить судью
          <select v-model="refereeId" required>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ refereeTitle(u) }}</option>
          </select>
        </label>
        <button class="btn assign-btn" type="submit" :disabled="pending || !users.length">Назначить</button>
        <p v-if="!users.length" class="muted">Сначала поставьте кому-то роль судьи в админке.</p>
      </form>
    </AdminOnly>

    <div class="match-tabs" role="tablist">
      <button type="button" :class="{ on: tab === 'overview' }" @click="setTab('overview')"><span class="tab-label">Обзор</span></button>
      <button type="button" :class="{ on: tab === 'stats' }" @click="setTab('stats')"><span class="tab-label"><span class="full">Статистика</span><span class="short">Стат.</span></span></button>
      <button type="button" :class="{ on: tab === 'lineups' }" @click="setTab('lineups')"><span class="tab-label">Составы</span></button>
      <button type="button" :class="{ on: tab === 'protocol' }" @click="setTab('protocol')"><span class="tab-label">Протокол</span></button>
    </div>

    <div v-if="tab === 'overview'" class="overview">
      <div class="stack">
        <FutsalBoard
          :match="match"
          :events="events"
          :home-label="teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')"
          :away-label="teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда')"
        />
        <section class="card events">
          <header><b>События</b><span>{{ heroScore }}</span></header>
          <p v-if="!periodBlocks.length" class="muted empty-events">Событий пока нет</p>
          <template v-else>
            <section v-for="block in periodBlocks" :key="block.period">
              <div class="half">
                <span>{{ block.label }}</span>
                <span>{{ blockScore(block.score) }}</span>
              </div>
              <div v-for="ev in block.items" :key="ev.id" class="ev" :class="[ev.home ? 'home' : 'away', { 'goal-enter': freshGoal(ev) }]">
                <div class="who">
                  <b>
                    <RouterLink v-if="ev.playerId" :to="`/players/${ev.playerId}`">{{ playerTag(registeredName(ev), ev.playerJersey) || labelOf(eventLabel, ev.eventType) }}</RouterLink>
                    <template v-else>{{ playerTag(registeredName(ev), ev.playerJersey) || labelOf(eventLabel, ev.eventType) }}</template>
                  </b>
                  <span v-if="isOwnGoal(ev)">Автогол</span>
                  <span v-else-if="ev.eventType === 'GOAL' && registeredName(ev, 'secondary')">пас · {{ playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey) }}</span>
                  <span v-if="ev.scoreline" class="run">{{ blockScore(ev.scoreline) }}</span>
                </div>
                <em v-if="shownMinute(ev) != null">{{ shownMinute(ev) }}'</em>
              </div>
            </section>
            <div class="total"><span>Итог</span><b>{{ heroScore }}</b></div>
          </template>
        </section>
      </div>
      <aside class="rail">
        <section class="card">
          <header><b>Форма</b><span>последние 5</span></header>
          <div v-if="formError" class="fail">
            <p>Форма не загрузилась</p>
            <button class="btn secondary" type="button" @click="loadForms(match.homeTeamId, match.awayTeamId)">Повторить</button>
          </div>
          <p v-else-if="!formReady" class="muted">Загрузка…</p>
          <template v-else>
            <div class="form-row">
              <TeamCrest :src="teams.logo(match.homeTeamId)" :name="teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда')" :size="28" />
              <b>{{ teams.fullName(match.homeTeamId, match.homeTeamName || 'Команда') }}</b>
              <span class="marks">
                <i v-for="(mark, index) in homeMarks" :key="`h-${index}`" :class="markClass(mark)">{{ mark }}</i>
                <em v-if="!homeMarks.length">нет матчей</em>
              </span>
            </div>
            <div class="form-row">
              <TeamCrest :src="teams.logo(match.awayTeamId)" :name="teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда')" :size="28" />
              <b>{{ teams.fullName(match.awayTeamId, match.awayTeamName || 'Команда') }}</b>
              <span class="marks">
                <i v-for="(mark, index) in awayMarks" :key="`a-${index}`" :class="markClass(mark)">{{ mark }}</i>
                <em v-if="!awayMarks.length">нет матчей</em>
              </span>
            </div>
          </template>
        </section>
        <section class="card about">
          <header><b>О матче</b></header>
          <dl>
            <div><dt>Турнир</dt><dd><RouterLink v-if="match.tournamentId" :to="`/tournaments/${match.tournamentId}`">{{ tournamentTitle }}</RouterLink><template v-else>{{ tournamentTitle }}</template></dd></div>
            <div><dt>Стадия</dt><dd>{{ stageText }}</dd></div>
            <div><dt>Дата</dt><dd>{{ matchCardWhen(match) }}</dd></div>
            <div><dt>Место</dt><dd>{{ placeText }}</dd></div>
            <div><dt>Судьи</dt><dd>{{ publicReferees.length ? publicReferees.join(', ') : 'Судьи не назначены' }}</dd></div>
          </dl>
          <RouterLink v-if="canUsePad" class="btn secondary pad" :to="`/referee/matches/${match.id}`">Открыть пульт</RouterLink>
        </section>
      </aside>
    </div>

    <div v-else-if="tab === 'stats'">
      <MatchAnalystStats :match-id="String(match.id)" />
    </div>

    <div v-else-if="tab === 'lineups'" class="grid lineups">
      <div class="panel">
        <MatchLineupBoard
          :side="lineups?.home"
          :editable="auth.canManageLeague || assignedReferee || isCaptainOf(match.homeTeamId)"
          :pending="pending"
          @save="saveLineup"
        />
      </div>
      <div class="panel">
        <MatchLineupBoard
          :side="lineups?.away"
          :editable="auth.canManageLeague || assignedReferee || isCaptainOf(match.awayTeamId)"
          :pending="pending"
          @save="saveLineup"
        />
      </div>
    </div>

    <div v-else-if="tab === 'protocol'" class="panel">
      <h2>Протокол</h2>
      <p v-if="shootoutScore" class="shootout-line">{{ shootoutScore }}</p>
      <p v-if="!timeline.length" class="muted">Событий пока нет</p>
      <ul class="timeline">
        <li v-for="ev in timeline" :key="ev.id" :class="[protocolSide(ev), { 'goal-enter': freshGoal(ev) }]">
          <span v-if="shownMinute(ev) != null" class="t">{{ shownMinute(ev) }}'</span>
          <div>
            <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
            <em v-if="isOwnGoal(ev)" class="own-tag">Автогол</em>
            <p class="muted">
              <template v-if="ev.playerId || playerTag(registeredName(ev), ev.playerJersey)">
                <RouterLink v-if="ev.playerId" class="player-link" :to="`/players/${ev.playerId}`">{{ playerTag(registeredName(ev), ev.playerJersey) || 'Игрок' }}</RouterLink>
                <template v-else>{{ playerTag(registeredName(ev), ev.playerJersey) }}</template>
                <template v-if="ev.secondaryPlayerId && ev.eventType === 'GOAL' && !isOwnGoal(ev)">
                  · пас <RouterLink class="player-link" :to="`/players/${ev.secondaryPlayerId}`">{{ playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey) }}</RouterLink>
                </template>
                <template v-else-if="ev.secondaryPlayerId && ev.eventType === 'SUBSTITUTION'">
                  → <RouterLink class="player-link" :to="`/players/${ev.secondaryPlayerId}`">{{ playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey) }}</RouterLink>
                </template>
                <template v-else-if="protocolExtra(ev)"> {{ protocolExtra(ev) }}</template>
                ·
              </template>
              <template v-else-if="isOwnGoal(ev)">Автогол · </template>
              <span class="when-label">{{ periodLabel(ev.period, match.sportCode, match.periodCount) }}</span>
            </p>
          </div>
        </li>
      </ul>
    </div>

  </section>
  <NotFoundView v-else-if="missing" heading="Матч не найден" />
  <section v-else-if="error" class="match-state">
    <strong>Матч не загрузился</strong>
    <button class="btn secondary" type="button" @click="load">Повторить</button>
  </section>
</template>

<style scoped>
.league-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.55rem 0.85rem;
  background: #eef4f9;
  color: var(--navy);
  font-weight: 800;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-radius: 10px;
}
.venue-line {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}
.match-title {
  margin: 0;
  text-align: center;
  font-size: clamp(1.15rem, 2.6vw, 1.6rem);
}
.board {
  --crest: 96px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  grid-template-areas:
    "when when when"
    "home score away"
    "under under under";
  gap: 0.4rem 0.85rem;
  align-items: center;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 1rem 0.9rem 1.05rem;
}
.when { grid-area: when; text-align: center; }
.under {
  grid-area: under;
  display: grid;
  justify-items: center;
  gap: 0.28rem;
}
.club {
  grid-area: home;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  max-width: 100%;
}
.club.away {
  grid-area: away;
  justify-content: flex-end;
}
.who {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
  max-width: 100%;
  color: inherit;
  text-decoration: none;
}
.club.away .who { flex-direction: row-reverse; }
.who :deep(.team-crest) {
  flex: 0 0 var(--crest);
  width: var(--crest) !important;
  height: var(--crest) !important;
  min-width: var(--crest);
  min-height: var(--crest);
  max-width: var(--crest);
  max-height: var(--crest);
  object-fit: contain;
  object-position: center;
}
.who strong {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  color: #00205B;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.15;
  text-align: start;
  overflow-wrap: break-word;
  hyphens: auto;
  -webkit-hyphens: auto;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.club.away .who strong { text-align: end; }
.star {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #c5ced8;
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0;
}
.star.on { color: var(--ice); }
.center {
  grid-area: score;
  text-align: center;
  justify-self: center;
  min-width: 4.5rem;
  padding: 0 0.2rem;
}
.when, .state, .clock-note { margin: 0; font-size: 0.78rem; color: var(--muted); }
.state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-weight: 800;
}
.state.done { text-transform: none; letter-spacing: 0; }
.score {
  margin: 0.15rem 0;
  font-size: clamp(2rem, 5.4vw, 2.75rem);
  font-weight: 800;
  line-height: 0.95;
  color: var(--navy);
  white-space: nowrap;
}
.score.shootout {
  font-size: clamp(1.05rem, 3.8vw, 1.7rem);
  letter-spacing: 0;
}
.clock {
  margin: 0.2rem 0 0;
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--ice);
}
.clock.expired { color: var(--danger); }
.sheet {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.half-head {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0.85rem;
  background: #f4f7fb;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.ev {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid var(--line);
  font-size: 0.88rem;
}
.ev.away { grid-template-columns: auto auto 1fr; }
.ev.away .who-ev { order: 3; text-align: right; justify-items: end; }
.ev.away .mark { order: 2; }
.ev.away em { order: 1; }
.who-ev { display: grid; gap: 0.1rem; min-width: 0; }
.who-ev a { color: inherit; }
.who-name {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-weight: 800;
}
.ev b { font-weight: 800; color: var(--navy); }
.ev em { font-style: normal; color: var(--muted); font-variant-numeric: tabular-nums; }
.line { color: var(--muted); font-size: 0.78rem; font-weight: 700; }
.mark {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--navy);
}
.mark.yellow_card { border-radius: 3px; background: #f5c400; }
.mark.red_card { border-radius: 3px; background: var(--danger); }
.mark.substitution { border-radius: 2px; background: var(--ice); }
.empty-line { padding: 0.9rem; margin: 0; }
.lineups { grid-template-columns: 1fr 1fr; gap: 1rem; align-items: start; }
.lineups > .panel { min-width: 0; }
.motm {
  margin: 0.15rem 0 0.1rem;
  display: grid;
  justify-items: center;
  gap: 0.05rem;
}
.motm span {
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}
.motm-who {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--navy);
  font-weight: 800;
  font-size: 0.95rem;
}
.motm-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
h2 { font-size: 1.2rem; margin-bottom: 0.35rem; }
.assign-card h2 { margin: 0; font-size: 1.5rem; }
.assign-btn { width: 100%; }
.recent { min-width: 0; }
.recent .panel { min-width: 0; overflow: hidden; }
.recent-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  grid-template-areas:
    "home score away"
    "home when away";
  column-gap: 0.4rem;
  align-items: center;
  justify-items: center;
  min-width: 0;
  max-width: 100%;
  margin: 0 -0.15rem;
  padding: 0.4rem 0.15rem;
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
}
.recent-row + .recent-row { border-top: 1px solid var(--line); }
.recent-row:hover { background: var(--surface-hover); color: inherit; }
.recent-row:focus-visible {
  outline: 2px solid var(--ice);
  outline-offset: 1px;
}
.recent-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.12rem;
  min-width: 0;
  width: 100%;
  max-width: 100%;
}
.recent-side.home { grid-area: home; }
.recent-side.away { grid-area: away; }
.recent-name {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
  font-size: 0.68rem;
  line-height: 1.15;
  font-weight: 700;
  color: var(--navy);
}
.recent-score {
  grid-area: score;
  font-weight: 800;
  font-size: 1rem;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  white-space: nowrap;
}
.recent-when {
  grid-area: when;
  font-size: 0.68rem;
  line-height: 1.15;
  color: var(--muted);
  white-space: nowrap;
}
.recent-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0.25rem 0;
  color: inherit;
  text-decoration: none;
}
h2 a { color: inherit; }
.protocol-form { margin: 0.4rem 0 0.8rem; }
.shootout-line {
  margin: 0 0 0.8rem;
  font-weight: 800;
  color: var(--navy);
}
.check-line {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-weight: 700;
  color: var(--navy);
}
.own-tag {
  justify-self: start;
  width: max-content;
  margin-left: 0.35rem;
  color: var(--navy);
  background: color-mix(in srgb, var(--ice) 35%, #fff);
  border-radius: 999px;
  padding: 0.05rem 0.45rem;
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 800;
}
.ev.home .own-tag { margin-left: 0; }
.ev.away .own-tag { justify-self: end; }
.proto-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem 0.7rem;
  align-items: end;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--line);
}
.proto-row small { display: block; }
.proto-kind { min-width: 4.5rem; }
.player { min-width: 12rem; flex: 1 1 14rem; max-width: 20rem; }
.minute { min-width: 5.5rem; max-width: 7rem; }
.timeline { list-style: none; margin: 0.75rem 0 0; padding: 0; display: grid; gap: 0.5rem; }
.timeline li {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 0.75rem;
  align-items: start;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #f6f9fc;
}
.timeline li.home {
  grid-template-columns: 1fr 72px;
}
.timeline li.home .t { order: 2; text-align: right; }
.timeline li.home > div { order: 1; text-align: left; }
.timeline li.away { text-align: right; }
.timeline li.away > div { text-align: right; }
.when-label { white-space: nowrap; }
.player-link { color: var(--navy); font-weight: 800; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; font-size: 0.85rem; padding-top: 0.15rem; }
@media (max-width: 719px) {
  .board {
    --crest: 72px;
    align-items: start;
    padding: 0.7rem 0.28rem 0.8rem;
    gap: 0.35rem 0.28rem;
  }
  .club {
    position: relative;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
    padding-top: 0.95rem;
  }
  .club.away { align-items: flex-end; }
  .star {
    position: absolute;
    top: 0;
    font-size: 0.95rem;
    line-height: 1;
  }
  .club .star { left: 0; }
  .club.away .star { left: auto; right: 0; }
  .who {
    flex-direction: column;
    align-items: flex-start;
    width: 100%;
    gap: 0.35rem;
  }
  .club.away .who {
    flex-direction: column;
    align-items: flex-end;
  }
  .who strong { width: 100%; text-align: start; }
  .club.away .who strong { text-align: end; }
  .center {
    align-self: start;
    min-width: 0;
    margin-top: calc(0.95rem + (var(--crest) - 2rem) / 2);
    padding: 0;
  }
  .lineups { grid-template-columns: 1fr; }
}
@media (max-width: 370px) {
  .board {
    --crest: 72px;
    padding-left: 0.28rem;
    padding-right: 0.28rem;
    gap: 0.35rem 0.3rem;
  }
  .score { font-size: 1.75rem; }
}
.match-state {
  display: grid;
  justify-items: center;
  gap: 0.7rem;
  padding: 1.4rem 1rem;
  background: #fff;
  border-radius: 18px;
  text-align: center;
}
.match-state h1 { font-size: 1.4rem; text-transform: none; }
.bar {
  display: block;
  width: min(100%, 28rem);
  height: 4.5rem;
  border-radius: 16px;
  background: linear-gradient(90deg, #d5e0ec 25%, #eef3f8 50%, #d5e0ec 75%);
  background-size: 200% 100%;
}
.bar.short { width: min(100%, 16rem); height: 2.2rem; }
.pitch {
  background: var(--ink);
  color: #fff;
  border-radius: 22px;
  padding: 1rem 1.1rem 1.15rem;
  display: grid;
  justify-items: center;
  text-align: center;
}
.pitch-top, .pitch-score {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.pitch-top a, .pitch-top span { color: var(--ice); font-weight: 800; text-decoration: none; }
.pill {
  border-radius: 999px;
  padding: 0.18rem 0.65rem;
  font-style: normal;
  font-weight: 800;
  font-size: 0.75rem;
  background: rgba(255,255,255,0.12);
  color: #fff;
}
.pill.live { background: var(--status-live-soft); color: #ffd2cb; }
.pill.paused { background: var(--status-warn-soft); color: #ffe0a8; }
.pill.finished { background: rgba(255,255,255,0.14); }
.pill.cancelled { color: rgba(255,255,255,0.65); }
.pitch-score { margin-top: 0.8rem; align-items: flex-start; }
.pitch-side {
  display: grid;
  grid-template-areas: "star" "who";
  justify-items: center;
  align-content: start;
  gap: 0.15rem;
  min-width: 0;
  flex: 1;
}
.pitch-side .fav-star { grid-area: star; }
.pitch-side .pitch-who { grid-area: who; }
.pitch-who {
  display: grid;
  justify-items: center;
  gap: 0.35rem;
  color: #fff;
  text-decoration: none;
  min-width: 0;
}
.pitch-who strong {
  color: #fff;
  font-size: 1rem;
  font-weight: 700;
  text-transform: none;
}
.nums b {
  display: block;
  color: #fff;
  font-size: clamp(2.4rem, 5vw, 3.4rem);
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
}
.pens, .clock { margin: 0.15rem 0 0; color: rgba(255,255,255,0.75); font-size: 0.85rem; }
.pitch-meta { margin: 0.75rem 0 0; color: rgba(255,255,255,0.78); }
.fav-star {
  border: 0;
  background: transparent;
  color: rgba(255,255,255,0.45);
  cursor: pointer;
  font-size: 1rem;
}
.fav-star.on { color: var(--ice); }
.match-tabs {
  display: flex;
  gap: 0.2rem;
  overflow-x: auto;
  min-width: 0;
  max-width: 100%;
  position: sticky;
  top: 56px;
  z-index: 4;
  background: #fff;
  border-radius: 18px;
  padding: 0.25rem;
}
.match-tabs button {
  flex: 1 1 0;
  display: flex;
  justify-content: center;
  align-items: flex-end;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-weight: 800;
  padding: 0.15rem 0.35rem 0;
  cursor: pointer;
  white-space: nowrap;
}
.match-tabs .tab-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.55rem 0.05rem 0.42rem;
  border-bottom: 3px solid transparent;
  line-height: 1.2;
}
.match-tabs button.on { color: var(--navy); }
.match-tabs button.on .tab-label { border-bottom-color: var(--ice); color: var(--navy); }
.match-tabs .short { display: none; }
.overview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 0.9rem;
  align-items: start;
}
.card {
  background: #fff;
  border-radius: 18px;
  padding: 0.85rem 0.95rem 1rem;
  min-width: 0;
}
.card header, .half, .total {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: center;
}
.card header b, .half span, .total span { color: var(--navy); }
.half, .ev { border-top: 1px solid var(--line); padding: 0.45rem 0; }
.ev { display: flex; justify-content: space-between; gap: 0.6rem; }
.ev .who { display: grid; justify-items: end; text-align: right; min-width: 0; }
.ev.away .who { justify-items: start; text-align: left; }
.ev b a { color: inherit; text-decoration: none; }
.run { color: var(--muted); font-variant-numeric: tabular-nums; font-weight: 800; }
.total {
  margin-top: 0.35rem;
  background: #eef5fb;
  border-radius: 12px;
  padding: 0.55rem 0.7rem;
}
.total b { color: var(--navy); font-variant-numeric: tabular-nums; }
.rail { display: grid; gap: 0.75rem; }
.form-row, .about dl div {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 0.5rem;
  align-items: center;
  padding: 0.4rem 0;
}
.form-row b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}
.form-row .marks { align-self: center; }
.about dl { margin: 0; }
.about dl div { grid-template-columns: 5.5rem minmax(0, 1fr); }
.about dt { color: var(--muted); font-weight: 700; }
.about dd { margin: 0; text-align: right; color: var(--navy); font-weight: 700; }
.marks { display: flex; gap: 0.25rem; }
.marks i {
  width: 1.15rem;
  height: 1.15rem;
  border-radius: 4px;
  display: grid;
  place-items: center;
  color: #fff;
  font-style: normal;
  font-size: 0.68rem;
  font-weight: 800;
  background: var(--muted);
}
.marks i.win { background: var(--status-ok); }
.marks i.draw { background: var(--status-warn); }
.marks i.loss { background: var(--danger); }
.marks em { color: var(--muted); font-style: normal; font-size: 0.78rem; }
.fail { display: grid; justify-items: center; gap: 0.5rem; }
.pad { justify-self: start; margin-top: 0.6rem; }
.overview > .stack { align-items: start; }
.card.events { align-self: start; height: auto; }
.empty-events { margin: 0; padding: 0.4rem 0 0.15rem; }
@media (max-width: 720px) {
  .overview { grid-template-columns: 1fr; }
  .match-tabs { flex-wrap: wrap; overflow: visible; }
  .match-tabs button { flex: 1 1 auto; min-width: max-content; }
  .match-tabs .full { display: none; }
  .match-tabs .short { display: inline; }
  .pitch { border-radius: 18px; }
  .pitch-who strong { font-size: 0.85rem; }
}
</style>
