<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../../api/client'
import { eventDetail, eventLabel, formatClock, labelOf, periodLabel, playerTag } from '../../lib/format'
import { registeredName } from '../../lib/playerTitle'
import { finishConfirmQuestion, protocolMinute, protocolScore, publicScore } from '../../lib/match'
import { reconciledEvent, refereePenaltyCaption, unclearSend } from '../../lib/refereePad'
import { apiError } from '../../lib/errors'
import { useMatchClock } from '../../lib/useMatchClock'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import { useAuthStore } from '../../stores/auth'
import AnalystAssignForm from '../../components/AnalystAssignForm.vue'
import FutsalBoard from '../../components/FutsalBoard.vue'
import MatchStageNote from '../../components/MatchStageNote.vue'
import TeamCrest from '../../components/TeamCrest.vue'

type EventKind = 'GOAL' | 'YELLOW_CARD' | 'RED_CARD' | 'SUBSTITUTION'
type Step = 'team' | 'player' | 'assist' | 'incoming'

type Sheet = {
  kind: EventKind
  step: Step
  teamId?: string
  player?: any
  ownGoal?: boolean
}

const route = useRoute()
const auth = useAuthStore()
const match = ref<any>(null)
const shootout = ref<any>(null)
const events = ref<any[]>([])
const referees = ref<any[]>([])
const lineups = ref<any>(null)
const homeRoster = ref<any[]>([])
const awayRoster = ref<any[]>([])
const message = ref('')
const error = ref('')
const flowError = ref('')
const flowMessage = ref('')
const eventError = ref('')
const keeperError = ref('')
const shootoutError = ref('')
const finishError = ref('')
const loadError = ref('')
const booting = ref(true)
const flowPending = ref(false)
const flowPath = ref('')
const finishPending = ref(false)
const finishAsk = ref(false)
const eventPending = ref(false)
const shootoutPending = ref(false)
const keeperTeam = ref('')
const voidingId = ref('')
const protocolDirty = ref(false)
const sheet = ref<Sheet | null>(null)
const dialogRoot = ref<HTMLElement | null>(null)
const eventAlert = ref<HTMLElement | null>(null)
const finishAlert = ref<HTMLElement | null>(null)
let opener: HTMLElement | null = null
const heldClock = ref(false)
const teams = useTeamDirectory()
const { elapsed, remaining, expired } = useMatchClock(match)
let liveClient: Client | null = null
let liveSub: StompSubscription | null = null

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))
const live = computed(() => match.value?.status === 'LIVE' || match.value?.status === 'PAUSED')
const playoffDraw = computed(() => {
  const current = match.value
  if (!current || current.bracketRound == null) return false
  return Number(current.homeScore) === Number(current.awayScore)
})
const shootoutOpen = computed(() => live.value && playoffDraw.value)
const shootoutLine = computed(() => {
  const current = match.value
  if (!current) return ''
  const series = shootout.value
  if (series?.kicks?.length) {
    return publicScore({
      homeScore: current.homeScore,
      awayScore: current.awayScore,
      penaltyHome: series.penaltyHome,
      penaltyAway: series.penaltyAway,
    })
  }
  return publicScore(current)
})
const turnTeamId = computed(() => {
  const current = match.value
  const kicks = shootout.value?.kicks ?? []
  if (!current || !shootoutOpen.value || shootout.value?.decisive) return ''
  let home = 0
  let away = 0
  for (const kick of kicks) {
    if (kick.teamId === current.homeTeamId) home += 1
    else away += 1
  }
  if (home === away) return current.homeTeamId
  if (home === away + 1) return current.awayTeamId
  return ''
})
const protocol = computed(() => [...events.value].reverse())
const canControl = computed(() => {
  if (auth.canManageLeague) return true
  const id = auth.user?.id
  if (!id) return false
  return referees.value.some((row) => row.refereeId === id)
})

function nameOf(player: any) {
  return playerTag(registeredName(player), player.jerseyNumber)
}

function rosterOf(teamId?: string) {
  if (!match.value || !teamId) return []
  return teamId === match.value.homeTeamId ? homeRoster.value : awayRoster.value
}

const sheetTitle = computed(() => {
  const current = sheet.value
  if (!current) return ''
  if (current.kind === 'GOAL') {
    if (current.ownGoal) return 'Кто забил в свои ворота?'
    if (current.step === 'team') return 'Кто забил?'
    if (current.step === 'player') return 'Кто забил гол?'
    return 'Кто отдал передачу?'
  }
  if (current.kind === 'SUBSTITUTION') {
    if (current.step === 'team') return 'Замена: какая команда?'
    if (current.step === 'player') return 'Кто уходит?'
    return 'Кто выходит?'
  }
  if (current.kind === 'YELLOW_CARD') {
    return current.step === 'team' ? 'Кому жёлтая?' : 'Кому показать жёлтую?'
  }
  return current.step === 'team' ? 'Кому красная?' : 'Кому показать красную?'
})

const stepCount = computed(() => {
  const current = sheet.value
  if (!current) return 2
  if (current.kind === 'SUBSTITUTION') return 3
  if (current.kind === 'GOAL' && !current.ownGoal) return 3
  return 2
})

function stepIndex(step: Step) {
  if (step === 'team') return 1
  if (step === 'player') return 2
  return 3
}

const sheetHint = computed(() => {
  const current = sheet.value
  if (!current) return ''
  if (current.kind === 'GOAL' && current.ownGoal) {
    return 'Очко получит соперник. Передачи нет.'
  }
  if (current.kind === 'GOAL' && current.step === 'assist' && current.player) {
    return `Гол: ${nameOf(current.player)}. Если паса не было — пропустите. Автогол — мяч в свои ворота.`
  }
  if (current.step === 'team') return heldClock.value ? 'Часы стоят. Сначала команда.' : 'Сначала команда.'
  return heldClock.value ? 'Часы стоят. Выберите игрока из заявки.' : 'Выберите игрока из заявки.'
})

function eventKey(event: { id?: string | number | null }) {
  return event?.id == null ? '' : String(event.id)
}

function serverCaughtUp(server: any[], local: any[]) {
  return local.every((event) => {
    const id = eventKey(event)
    if (!id) return true
    const row = server.find((item) => eventKey(item) === id)
    if (!row) return false
    return !event.voided || !!row.voided
  })
}

function mergeServerEvents(server: any[], local: any[]) {
  const voidedLocal = new Map(
    local.filter((event) => eventKey(event) && event.voided).map((event) => [eventKey(event), event]),
  )
  const merged = server.map((event) => {
    const localVoid = voidedLocal.get(eventKey(event))
    if (localVoid && !event.voided) {
      return { ...event, voided: true, voidedAt: localVoid.voidedAt ?? event.voidedAt }
    }
    return event
  })
  const known = new Set(merged.map((event) => eventKey(event)))
  for (const event of local) {
    const id = eventKey(event)
    if (id && !known.has(id)) merged.push(event)
  }
  return merged
}

function paintScore(current = match.value, rows = events.value) {
  if (!current) return
  const counted = protocolScore(current, rows)
  match.value = { ...current, homeScore: counted.home, awayScore: counted.away }
}

function upsertProtocolEvent(event: any) {
  if (!event || !match.value) return
  const id = eventKey(event)
  if (!id) {
    events.value = [...events.value, event]
  } else {
    const index = events.value.findIndex((row) => eventKey(row) === id)
    if (index === -1) {
      events.value = [...events.value, event]
    } else {
      const next = events.value.slice()
      next[index] = { ...next[index], ...event }
      events.value = next
    }
  }
  protocolDirty.value = true
  paintScore()
}

function applyLive(live: any) {
  if (!match.value || !live) return
  if (live.matchId && String(live.matchId) !== String(match.value.id)) return
  if (live.lastEvent?.id) upsertProtocolEvent(live.lastEvent)
  const counted = protocolScore(match.value, events.value)
  const frameScore = typeof live.homeScore === 'number' && typeof live.awayScore === 'number'
  match.value = {
    ...match.value,
    status: live.status ?? match.value.status,
    homeScore: protocolDirty.value || live.lastEvent ? counted.home : (frameScore ? live.homeScore : counted.home),
    awayScore: protocolDirty.value || live.lastEvent ? counted.away : (frameScore ? live.awayScore : counted.away),
    gameTimeSeconds: live.gameTimeSeconds ?? match.value.gameTimeSeconds,
    period: live.period ?? match.value.period,
    periodCount: live.periodCount ?? match.value.periodCount,
    periodLengthSeconds: live.periodLengthSeconds ?? match.value.periodLengthSeconds,
    clockRunningSince: 'clockRunningSince' in live ? live.clockRunningSince : match.value.clockRunningSince,
    sportCode: live.sportCode ?? match.value.sportCode,
  }
}

function connectLive() {
  if (liveClient) return
  liveClient = new Client({
    webSocketFactory: () => new SockJS('/ws') as WebSocket,
    connectHeaders: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
    reconnectDelay: 4000,
    onConnect: () => {
      liveSub?.unsubscribe()
      liveSub = liveClient?.subscribe(`/topic/matches/${route.params.id}`, (message: IMessage) => {
        try {
          applyLive(JSON.parse(message.body))
        } catch {
          /* ignore a bad frame */
        }
      }) ?? null
    },
  })
  liveClient.activate()
}

function disconnectLive() {
  liveSub?.unsubscribe()
  liveSub = null
  const current = liveClient
  liveClient = null
  void current?.deactivate()
}

async function reload() {
  if (!match.value) booting.value = true
  try {
  await teams.load()
  const id = route.params.id
  const [m, e, l, r, s] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
    api.get(`/matches/${id}/lineups`),
    api.get(`/matches/${id}/referees`),
    api.get(`/matches/${id}/shootout`),
  ])
  const serverEvents = Array.isArray(e.data) ? e.data : []
  const merged = mergeServerEvents(serverEvents, events.value)
  const caughtUp = serverCaughtUp(serverEvents, events.value)
  shootout.value = s.data
  referees.value = r.data
  // The protocol is the score. A match GET can come back from before the save
  // and would otherwise put the header back to 0:0 while the goal stays listed.
  match.value = { ...m.data, ...protocolScorePatch(m.data, merged) }
  loadError.value = ''
  if (caughtUp) protocolDirty.value = false
  if (!canControl.value) {
    events.value = []
    lineups.value = null
    homeRoster.value = []
    awayRoster.value = []
    protocolDirty.value = false
    return
  }
  events.value = merged
  if (!caughtUp) paintScore()
  lineups.value = l.data
  const [home, away] = await Promise.all([
    api.get(`/teams/${m.data.homeTeamId}/members`),
    api.get(`/teams/${m.data.awayTeamId}/members`),
  ])
  homeRoster.value = home.data
  awayRoster.value = away.data
  } catch {
    loadError.value = 'Не удалось загрузить матч'
  } finally {
    booting.value = false
  }
}

function protocolScorePatch(current: any, rows: any[]) {
  const counted = protocolScore(current, rows)
  return { homeScore: counted.home, awayScore: counted.away }
}

async function refreshMatch() {
  const { data } = await api.get(`/matches/${route.params.id}`)
  match.value = { ...data, ...protocolScorePatch(data, events.value) }
}

async function holdClock() {
  if (heldClock.value || match.value?.status !== 'LIVE') return
  try {
    await api.post(`/referee/matches/${route.params.id}/pause`)
    heldClock.value = true
    if (match.value) match.value = { ...match.value, status: 'PAUSED' }
  } catch (reason) {
    heldClock.value = false
    error.value = apiError(reason, 'Не удалось остановить часы.')
    return
  }
  try {
    await refreshMatch()
  } catch (reason) {
    error.value = apiError(reason, 'Часы остановлены, но табло не обновилось.')
  }
}

async function releaseClock() {
  if (!heldClock.value) return
  heldClock.value = false
  try {
    await api.post(`/referee/matches/${route.params.id}/resume`)
    await refreshMatch()
  } catch (reason) {
    error.value = apiError(reason, 'Часы не запустились. Нажмите «Продолжить».')
  }
}

async function closeSheet() {
  sheet.value = null
  await releaseClock()
}

const finishQuestion = computed(() => {
  const current = match.value
  const series = shootout.value
  const halfStillOpen = remaining.value > 0 || (current?.period ?? 1) < (current?.periodCount ?? 1)
  return finishConfirmQuestion({
    halfStillOpen,
    homeScore: current?.homeScore,
    awayScore: current?.awayScore,
    penaltyHome: series?.penaltyHome ?? current?.penaltyHome,
    penaltyAway: series?.penaltyAway ?? current?.penaltyAway,
    decisive: !!series?.decisive,
  })
})

const finishExtra = computed(() => (finishQuestion.value === 'Завершить матч?' ? '' : finishQuestion.value))

const boardScore = computed(() => {
  const current = match.value
  if (!current) return ''
  return `${Number(current.homeScore ?? 0)} : ${Number(current.awayScore ?? 0)}`
})

const clockKnown = computed(() => Number(match.value?.periodLengthSeconds) > 0)

async function recordKick(teamId: string, scored: boolean) {
  if (shootoutPending.value || finishPending.value) return
  shootoutError.value = ''
  shootoutPending.value = true
  try {
    const { data } = await api.post(`/referee/matches/${route.params.id}/shootout`, { teamId, scored })
    shootout.value = data
    await refreshMatch()
  } catch (reason) {
    shootoutError.value = apiError(reason, 'Удар не записался.')
  } finally {
    shootoutPending.value = false
  }
}

async function undoKick() {
  if (shootoutPending.value || finishPending.value) return
  shootoutError.value = ''
  shootoutPending.value = true
  try {
    const { data } = await api.delete(`/referee/matches/${route.params.id}/shootout/last`)
    shootout.value = data
    await refreshMatch()
  } catch (reason) {
    shootoutError.value = apiError(reason, 'Последний удар не убрался.')
  } finally {
    shootoutPending.value = false
  }
}

function openFinish() {
  if (flowPending.value || finishPending.value || finishAsk.value || sheet.value) return
  if (!live.value || (shootoutOpen.value && !shootout.value?.decisive)) return
  rememberOpener()
  finishError.value = ''
  finishAsk.value = true
}

async function action(path: string) {
  if (path === 'finish') {
    openFinish()
    return
  }
  if (flowPending.value || finishPending.value) return
  flowMessage.value = ''
  flowError.value = ''
  flowPath.value = path
  flowPending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    flowMessage.value = 'Готово.'
  } catch (e: any) {
    flowError.value = apiError(e, 'Действие не прошло.')
  } finally {
    flowPending.value = false
  }
}

async function confirmFinish() {
  if (finishPending.value || flowPending.value) return
  finishError.value = ''
  finishPending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/finish`)
    finishAsk.value = false
    await reload()
    flowMessage.value = 'Матч завершён.'
  } catch (e: any) {
    finishError.value = apiError(e, 'Не удалось завершить матч. Повторите попытку')
    await nextTick()
    finishAlert.value?.focus()
  } finally {
    finishPending.value = false
  }
}

function canUndo(ev: { voided?: boolean; eventType?: string }) {
  if (!live.value || ev.voided) return false
  return ev.eventType !== 'PERIOD_START' && ev.eventType !== 'PERIOD_END'
}

async function undoEvent(ev: { id: string; eventType?: string }) {
  if (eventPending.value || voidingId.value === ev.id) return
  const label = labelOf(eventLabel, ev.eventType)
  if (!confirm(`Убрать «${label}» из протокола? Счёт пересчитается по оставшимся голам.`)) return
  if (eventPending.value || voidingId.value === ev.id) return
  error.value = ''
  voidingId.value = ev.id
  try {
    const { data } = await api.post(`/referee/matches/${route.params.id}/events/${ev.id}/void`)
    upsertProtocolEvent(data)
    message.value = 'Событие убрано из протокола.'
    try {
      await reload()
    } catch {
      /* the voided row is already on the board */
    }
  } catch (e: any) {
    error.value = apiError(e, 'Событие не убралось.')
  } finally {
    voidingId.value = ''
  }
}

async function acceptSavedEvent(event: any) {
  upsertProtocolEvent(event)
  sheet.value = null
  eventError.value = ''
  message.value = 'В протоколе.'
  await releaseClock()
  try {
    await reload()
  } catch {
    /* the saved event is already on the board */
  }
}

async function addEvent(payload: Record<string, unknown>) {
  if (eventPending.value || finishPending.value) return
  eventError.value = ''
  eventPending.value = true
  const before = events.value.slice()
  try {
    const played = elapsed.value
    const { data } = await api.post(`/referee/matches/${route.params.id}/events`, {
      ...payload,
      ...(played > 0 ? { gameTime: played } : {}),
    })
    await acceptSavedEvent(data)
  } catch (e: any) {
    if (unclearSend(e)) {
      try {
        const { data } = await api.get(`/matches/${route.params.id}/events`)
        const rows = Array.isArray(data) ? data : []
        const found = reconciledEvent(before, rows, {
          eventType: typeof payload.eventType === 'string' ? payload.eventType : '',
          teamId: typeof payload.teamId === 'string' ? payload.teamId : '',
          playerId: typeof payload.playerId === 'string' ? payload.playerId : '',
          secondaryPlayerId: typeof payload.secondaryPlayerId === 'string' ? payload.secondaryPlayerId : '',
          metadata: payload.metadata && typeof payload.metadata === 'object'
            ? payload.metadata as { ownGoal?: boolean }
            : null,
        })
        if (found) {
          await acceptSavedEvent(found)
          return
        }
      } catch {
        /* The protocol could not be read. Leave the form and do not send again. */
      }
    }
    eventError.value = 'Не удалось добавить событие. Повторите попытку'
    await nextTick()
    eventAlert.value?.focus()
  } finally {
    eventPending.value = false
  }
}

async function openSheet(kind: EventKind) {
  if (eventPending.value || finishPending.value || finishAsk.value) return
  eventError.value = ''
  if (!live.value) {
    error.value = 'Сначала стартуйте матч.'
    return
  }
  if (kind === 'YELLOW_CARD' || kind === 'RED_CARD') {
    await holdClock()
  }
  rememberOpener()
  sheet.value = { kind, step: 'team' }
}

function recordTeamGoal(ownGoal: boolean) {
  const current = sheet.value
  if (!current?.teamId || eventPending.value) return
  addEvent({
    eventType: 'GOAL',
    teamId: current.teamId,
    metadata: ownGoal ? { ownGoal: true } : undefined,
  })
}

function recordTeamCard() {
  const current = sheet.value
  if (!current?.teamId || eventPending.value || current.kind === 'GOAL' || current.kind === 'SUBSTITUTION') return
  addEvent({ eventType: current.kind, teamId: current.teamId })
}

function pickTeam(teamId: string) {
  if (!sheet.value) return
  sheet.value = { ...sheet.value, teamId, step: 'player' }
}

function keeperChoices(teamId?: string) {
  if (!match.value || !teamId) return []
  const home = teamId === match.value.homeTeamId
  const side = home ? lineups.value?.home : lineups.value?.away
  if (side?.confirmed) return [...(side.starters ?? []), ...(side.bench ?? [])]
  return home ? homeRoster.value : awayRoster.value
}

function chosenKeeperId(teamId?: string) {
  if (!match.value || !teamId) return ''
  const picked = teamId === match.value.homeTeamId ? match.value.homeGoalkeeper : match.value.awayGoalkeeper
  return picked?.playerId || ''
}

function toggleOwnGoal() {
  const current = sheet.value
  if (!current || eventPending.value) return
  if (!rosterOf(current.teamId).length) {
    recordTeamGoal(true)
    return
  }
  sheet.value = { ...current, ownGoal: !current.ownGoal }
}

function pickPlayer(player: any) {
  const current = sheet.value
  if (!current?.teamId || eventPending.value) return
  if (current.kind === 'GOAL') {
    if (current.ownGoal) {
      addEvent({
        eventType: 'GOAL',
        teamId: current.teamId,
        playerId: player.playerId,
        metadata: { ownGoal: true },
      })
      return
    }
    sheet.value = { ...current, player, step: 'assist' }
    return
  }
  if (current.kind === 'SUBSTITUTION') {
    sheet.value = { ...current, player, step: 'incoming' }
    return
  }
  addEvent({ eventType: current.kind, teamId: current.teamId, playerId: player.playerId })
}

function confirmSubstitution(incomingId: string) {
  const current = sheet.value
  if (!current?.teamId || !current.player || !incomingId || eventPending.value) return
  addEvent({
    eventType: 'SUBSTITUTION',
    teamId: current.teamId,
    playerId: current.player.playerId,
    secondaryPlayerId: incomingId,
  })
}

function confirmGoal(assistPlayerId?: string | null, ownGoal = false) {
  if (eventPending.value) return
  const current = sheet.value
  if (!current?.teamId || !current.player) return
  const flagged = ownGoal || !!current.ownGoal
  addEvent({
    eventType: 'GOAL',
    teamId: current.teamId,
    playerId: current.player.playerId,
    secondaryPlayerId: flagged ? undefined : (assistPlayerId || undefined),
    metadata: flagged ? { ownGoal: true } : undefined,
  })
}

async function setKeeper(teamId: string, playerId: string) {
  if (!playerId || chosenKeeperId(teamId) === playerId || keeperTeam.value) return
  keeperError.value = ''
  keeperTeam.value = teamId
  try {
    await api.put(`/referee/matches/${route.params.id}/goalkeeper`, { teamId, playerId })
    message.value = 'Вратарь записан.'
    try {
      await reload()
    } catch {
      loadError.value = 'Не удалось загрузить матч'
    }
  } catch (e: any) {
    keeperError.value = apiError(e, 'Вратаря не удалось записать')
  } finally {
    keeperTeam.value = ''
  }
}

function backSheet() {
  const current = sheet.value
  if (!current) return
  if (current.step === 'incoming' || current.step === 'assist') {
    sheet.value = { ...current, step: 'player', player: undefined }
    return
  }
  if (current.step === 'player') {
    sheet.value = { kind: current.kind, step: 'team' }
    return
  }
  void closeSheet()
}

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const dialogOpen = computed(() => !!sheet.value || finishAsk.value)

function focusableControls(root: HTMLElement) {
  return [...root.querySelectorAll<HTMLElement>(FOCUSABLE)].filter((el) => {
    if (el.getAttribute('aria-hidden') === 'true') return false
    const style = window.getComputedStyle(el)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
}

function focusDialogField() {
  const root = dialogRoot.value
  if (!root) return
  const marked = root.querySelector<HTMLElement>('[data-initial-focus]')
  const fallback = focusableControls(root)[0] || root
  const target = marked && !marked.hasAttribute('disabled') ? marked : fallback
  target.focus()
}

function onDialogTab(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !dialogOpen.value) return
  const root = dialogRoot.value
  if (!root) return
  const items = focusableControls(root)
  if (!items.length) {
    event.preventDefault()
    root.focus()
    return
  }
  const active = document.activeElement
  const index = active instanceof HTMLElement ? items.indexOf(active) : -1
  if (event.shiftKey) {
    if (index <= 0) {
      event.preventDefault()
      items[items.length - 1].focus()
    }
    return
  }
  if (index < 0 || index === items.length - 1) {
    event.preventDefault()
    items[0].focus()
  }
}

function onDialogEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !dialogOpen.value) return
  if (finishPending.value || eventPending.value) return
  if (finishAsk.value) {
    finishAsk.value = false
    return
  }
  if (sheet.value) void closeSheet()
}

function bindDialogKeys() {
  window.addEventListener('keydown', onDialogEscape)
  document.addEventListener('keydown', onDialogTab, true)
}

function unbindDialogKeys() {
  window.removeEventListener('keydown', onDialogEscape)
  document.removeEventListener('keydown', onDialogTab, true)
}

function lockBackground() {
  const root = document.getElementById('app')
  if (!root) return
  root.setAttribute('inert', '')
  root.setAttribute('aria-hidden', 'true')
}

function releaseBackground() {
  const root = document.getElementById('app')
  if (!root) return
  root.removeAttribute('inert')
  root.removeAttribute('aria-hidden')
}

function rememberOpener() {
  if (dialogOpen.value) return
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
}

watch(dialogOpen, (open) => {
  unbindDialogKeys()
  if (!open) {
    releaseBackground()
    document.body.style.overflow = ''
    const back = opener
    opener = null
    if (back?.isConnected) back.focus()
    return
  }
  bindDialogKeys()
  document.body.style.overflow = 'hidden'
  lockBackground()
  focusDialogField()
}, { flush: 'post' })

onMounted(() => {
  reload().finally(connectLive)
})
onUnmounted(() => {
  unbindDialogKeys()
  releaseBackground()
  document.body.style.overflow = ''
  disconnectLive()
  if (!heldClock.value) return
  heldClock.value = false
  api.post(`/referee/matches/${route.params.id}/resume`).catch((reason) => {
    error.value = apiError(reason, 'Часы не запустились. Нажмите «Продолжить».')
  })
})
</script>

<template>
  <section class="pad">
    <RouterLink class="back" to="/referee">← К матчам</RouterLink>

    <div v-if="booting && !match" class="panel boot" role="status" aria-busy="true" aria-label="Загрузка матча">
      <span class="skeleton bar"></span>
      <span class="skeleton bar short"></span>
    </div>
    <div v-else-if="!match" class="panel state">
      <strong>Не удалось загрузить матч</strong>
      <p>Данные не заменяем нулями</p>
      <button class="btn" type="button" @click="reload">Повторить</button>
    </div>
    <div v-else-if="!canControl" class="panel state">
      <strong>Нет доступа к пульту</strong>
      <p>Проверьте аккаунт</p>
      <RouterLink class="btn" to="/profile">К профилю</RouterLink>
    </div>

    <template v-else>
      <div v-if="loadError" class="panel state slim">
        <strong>Не удалось загрузить матч</strong>
        <button class="btn" type="button" @click="reload">Повторить</button>
      </div>

      <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
        <p class="kicker">Пульт судьи</p>
        <div class="sides">
          <strong class="club">
            <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="28" />
            <span>{{ homeLabel }}</span>
          </strong>
          <div class="mid">
            <div class="score">{{ boardScore }}</div>
            <p v-if="refereePenaltyCaption(match)" class="pens">{{ refereePenaltyCaption(match) }}</p>
            <div v-if="live && clockKnown" class="clock" :class="{ expired }">{{ formatClock(remaining) }}</div>
          </div>
          <strong class="club away">
            <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="28" />
            <span>{{ awayLabel }}</span>
          </strong>
        </div>
        <p class="status-line">
          <span>{{ match.status === 'LIVE' ? 'В игре' : match.status === 'PAUSED' ? 'Пауза' : match.status === 'FINISHED' ? 'Завершён' : match.status === 'CANCELLED' ? 'Отменён' : 'Матч не начат' }}</span>
          <span v-if="match.period"> · {{ periodLabel(match.period, match.sportCode, match.periodCount) }}</span>
        </p>
        <MatchStageNote variant="protocol" :match="match" />
        <p v-if="heldClock" class="held">Матч на паузе, часы стоят.</p>
      </div>

      <div class="layout">
        <section class="panel block">
          <h2>Ход матча</h2>
          <div v-if="match.status === 'FINISHED'" class="state flat">
            <strong>Матч завершён</strong>
            <p>Счёт и протокол доступны для просмотра</p>
          </div>
          <div v-else class="flow">
            <button
              v-if="match.status === 'SCHEDULED'"
              class="btn large"
              type="button"
              :disabled="flowPending || finishPending"
              @click="action('start')"
            >Старт</button>
            <button
              v-else-if="match.status === 'LIVE'"
              class="btn large"
              type="button"
              :disabled="flowPending || finishPending"
              @click="action('pause')"
            >Пауза</button>
            <button
              v-else-if="match.status === 'PAUSED'"
              class="btn large"
              type="button"
              :disabled="flowPending || finishPending"
              @click="action('resume')"
            >Продолжить</button>
            <button
              v-if="live"
              class="btn large secondary"
              type="button"
              :disabled="flowPending || finishPending || (match.period ?? 1) >= match.periodCount"
              @click="action('next-period')"
            >Следующий тайм</button>
          </div>
          <p v-if="expired && live" class="form-ok">Время тайма вышло.</p>
          <p v-if="flowMessage" class="form-ok">{{ flowMessage }}</p>
          <p v-if="flowError" class="form-error">{{ flowError }}</p>
          <button v-if="flowError && flowPath" class="btn" type="button" :disabled="flowPending" @click="action(flowPath)">Повторить</button>
        </section>

        <section v-if="shootoutOpen" class="panel block shootout">
          <h2>Серия пенальти</h2>
          <p class="hint">
            Ничья в плей-офф. «Завершить матч» откроется, когда серия определит победителя.
            Первые пять раундов — основа, дальше бьют, пока один не забьёт, а другой не промахнётся.
          </p>
          <p v-if="shootout?.kicks?.length" class="pen-line">{{ shootoutLine }}</p>
          <p class="hint">{{ turnTeamId === match.homeTeamId ? `Бьёт ${homeLabel}` : turnTeamId === match.awayTeamId ? `Бьёт ${awayLabel}` : 'Серия решена' }}</p>
          <div class="kick-row">
            <button class="btn secondary" type="button" :disabled="shootoutPending || finishPending || turnTeamId !== match.homeTeamId" @click="recordKick(match.homeTeamId, true)">{{ homeLabel }}: гол</button>
            <button class="btn secondary" type="button" :disabled="shootoutPending || finishPending || turnTeamId !== match.homeTeamId" @click="recordKick(match.homeTeamId, false)">{{ homeLabel }}: мимо</button>
            <button class="btn secondary" type="button" :disabled="shootoutPending || finishPending || turnTeamId !== match.awayTeamId" @click="recordKick(match.awayTeamId, true)">{{ awayLabel }}: гол</button>
            <button class="btn secondary" type="button" :disabled="shootoutPending || finishPending || turnTeamId !== match.awayTeamId" @click="recordKick(match.awayTeamId, false)">{{ awayLabel }}: мимо</button>
          </div>
          <button v-if="shootout?.kicks?.length" class="btn secondary" type="button" :disabled="shootoutPending || finishPending" @click="undoKick">Убрать последний удар</button>
          <ol v-if="shootout?.kicks?.length" class="kick-list">
            <li v-for="kick in shootout.kicks" :key="kick.id">
              Раунд {{ kick.round }} · {{ kick.teamId === match.homeTeamId ? homeLabel : awayLabel }} · {{ kick.scored ? 'гол' : 'мимо' }}
            </li>
          </ol>
          <p v-if="shootoutError" class="form-error">{{ shootoutError }}</p>
        </section>

        <section v-if="match.status !== 'FINISHED'" class="panel block">
          <h2>События матча</h2>
          <div class="events">
            <button class="btn large goal" type="button" :disabled="eventPending || finishPending || !live" @click="openSheet('GOAL')">Гол</button>
            <button class="btn large yellow" type="button" :disabled="eventPending || finishPending || !live" @click="openSheet('YELLOW_CARD')">Жёлтая</button>
            <button class="btn large danger" type="button" :disabled="eventPending || finishPending || !live" @click="openSheet('RED_CARD')">Красная</button>
            <button class="btn large secondary" type="button" :disabled="eventPending || finishPending || !live" @click="openSheet('SUBSTITUTION')">Замена</button>
          </div>
        </section>

        <FutsalBoard
          :match="match"
          :events="events"
          :home-label="homeLabel"
          :away-label="awayLabel"
          interactive
          :pending="eventPending || finishPending"
          :live="live"
          @foul="(teamId) => addEvent({ eventType: 'FOUL', teamId })"
          @timeout="(teamId) => addEvent({ eventType: 'TIMEOUT', teamId })"
        />

        <section class="panel block">
          <h2>Протокол</h2>
          <p v-if="!protocol.length" class="hint">Событий пока нет</p>
          <div v-for="ev in protocol" :key="ev.id" class="proto" :class="{ voided: ev.voided }">
            <span v-if="protocolMinute(ev, match.periodLengthSeconds) != null" class="t">{{ protocolMinute(ev, match.periodLengthSeconds) }}'</span>
            <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
            <span class="who">{{ eventDetail(ev) || '—' }}</span>
            <button v-if="canUndo(ev)" class="btn ghost" type="button" :disabled="eventPending || voidingId === ev.id" @click="undoEvent(ev)">Убрать</button>
          </div>
          <p v-if="message" class="form-ok">{{ message }}</p>
          <p v-if="error" class="form-error">{{ error }}</p>
        </section>

        <aside class="side">
          <section class="panel block">
            <h2>Вратари</h2>
            <p class="hint">Выберите вратаря каждой команды</p>
            <div class="keeper">
              <h3>{{ homeLabel }}</h3>
              <p v-if="!keeperChoices(match.homeTeamId).length" class="hint">В составе никого.</p>
              <button
                v-for="p in keeperChoices(match.homeTeamId)"
                :key="p.playerId"
                class="btn secondary pick"
                :class="{ on: chosenKeeperId(match.homeTeamId) === p.playerId }"
                :disabled="keeperTeam === match.homeTeamId"
                type="button"
                @click="setKeeper(match.homeTeamId, p.playerId)"
              >{{ nameOf(p) }}</button>
            </div>
            <div class="keeper">
              <h3>{{ awayLabel }}</h3>
              <p v-if="!keeperChoices(match.awayTeamId).length" class="hint">В составе никого.</p>
              <button
                v-for="p in keeperChoices(match.awayTeamId)"
                :key="p.playerId"
                class="btn secondary pick"
                :class="{ on: chosenKeeperId(match.awayTeamId) === p.playerId }"
                :disabled="keeperTeam === match.awayTeamId"
                type="button"
                @click="setKeeper(match.awayTeamId, p.playerId)"
              >{{ nameOf(p) }}</button>
            </div>
            <p v-if="keeperError" class="form-error">{{ keeperError }}</p>
          </section>
          <AnalystAssignForm
            :match-id="String(match.id)"
            :home-label="homeLabel"
            :away-label="awayLabel"
          />
        </aside>

        <section v-if="live" class="panel block finish">
          <h2>Завершение матча</h2>
          <p>Отдельное подтверждение перед финишем</p>
          <button
            class="btn"
            type="button"
            :disabled="flowPending || finishPending || (shootoutOpen && !shootout?.decisive)"
            @click="openFinish"
          >Завершить матч</button>
        </section>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="sheet" class="overlay" @click.self="eventPending ? undefined : closeSheet()">
        <div
          ref="dialogRoot"
          class="popover"
          role="dialog"
          aria-modal="true"
          aria-labelledby="event-dialog-title"
          tabindex="-1"
        >
          <div class="dialog-head">
            <h2 id="event-dialog-title">{{ sheetTitle }}</h2>
            <button class="icon-x" type="button" aria-label="Закрыть" :disabled="eventPending" @click="closeSheet">×</button>
          </div>
          <p class="step">{{ stepIndex(sheet.step) }} / {{ stepCount }}</p>
          <p v-if="heldClock" class="form-ok">Матч на паузе, часы стоят.</p>
          <p class="hint">{{ sheetHint }}</p>
          <p v-if="eventPending" class="hint" role="status">Сохраняем…</p>
          <p v-if="eventError" ref="eventAlert" class="form-error" role="alert" tabindex="-1">{{ eventError }}</p>

          <div v-if="sheet.step === 'team'" class="pick-grid" role="group" aria-label="Команда">
            <button class="btn large" type="button" data-initial-focus :disabled="eventPending" @click="pickTeam(match.homeTeamId)">{{ homeLabel }}</button>
            <button class="btn large secondary" type="button" :disabled="eventPending" @click="pickTeam(match.awayTeamId)">{{ awayLabel }}</button>
          </div>

          <div v-else-if="sheet.step === 'player'" class="chip-col">
            <template v-if="sheet.kind === 'GOAL' && !rosterOf(sheet.teamId).length">
              <p class="hint">В заявке никого. Гол запишется на команду.</p>
              <button class="btn large" type="button" data-initial-focus :disabled="eventPending" @click="recordTeamGoal(false)">Гол команды</button>
              <button class="btn large" type="button" :disabled="eventPending" @click="recordTeamGoal(true)">Автогол</button>
            </template>
            <template v-else-if="sheet.kind === 'SUBSTITUTION' && rosterOf(sheet.teamId).length < 2">
              <p class="hint">Для замены нужны два игрока в заявке.</p>
            </template>
            <template v-else>
              <button
                v-if="sheet.kind === 'GOAL'"
                class="btn large"
                :class="{ on: sheet.ownGoal }"
                type="button"
                :disabled="eventPending"
                @click="toggleOwnGoal"
              >Автогол</button>
              <p v-if="!rosterOf(sheet.teamId).length" class="hint">В заявке никого.</p>
              <button
                v-if="sheet.kind !== 'GOAL' && sheet.kind !== 'SUBSTITUTION' && !rosterOf(sheet.teamId).length"
                class="btn large"
                type="button"
                :disabled="eventPending"
                @click="recordTeamCard"
              >Карточка команде</button>
              <button
                v-for="p in rosterOf(sheet.teamId)"
                :key="p.playerId"
                class="btn secondary pick"
                type="button"
                :disabled="eventPending"
                @click="pickPlayer(p)"
              >{{ nameOf(p) }}</button>
            </template>
          </div>

          <div v-else-if="sheet.step === 'incoming'" class="chip-col">
            <button
              v-for="p in rosterOf(sheet.teamId).filter((x: any) => x.playerId !== sheet?.player?.playerId)"
              :key="p.playerId"
              class="btn secondary pick"
              type="button"
              :disabled="eventPending"
              @click="confirmSubstitution(p.playerId)"
            >{{ nameOf(p) }}</button>
          </div>

          <div v-else class="chip-col">
            <button class="btn large on" type="button" :disabled="eventPending" @click="confirmGoal(null, true)">Автогол</button>
            <button class="btn large" type="button" :disabled="eventPending" @click="confirmGoal(null)">Без передачи</button>
            <button
              v-for="p in rosterOf(sheet.teamId).filter((x: any) => x.playerId !== sheet?.player?.playerId)"
              :key="p.playerId"
              class="btn secondary pick"
              type="button"
              :disabled="eventPending"
              @click="confirmGoal(p.playerId)"
            >{{ nameOf(p) }}</button>
          </div>

          <div class="sheet-nav">
            <button class="btn ghost" type="button" :disabled="eventPending" @click="backSheet">Назад</button>
            <button class="btn secondary" type="button" :disabled="eventPending" @click="closeSheet">Отмена</button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="finishAsk && match" class="overlay" @click.self="finishPending ? undefined : (finishAsk = false)">
        <div
          ref="dialogRoot"
          class="popover"
          role="dialog"
          aria-modal="true"
          aria-labelledby="finish-dialog-title"
          tabindex="-1"
        >
          <div class="dialog-head">
            <h2 id="finish-dialog-title">Завершить матч?</h2>
            <button class="icon-x" type="button" aria-label="Закрыть" :disabled="finishPending" @click="finishAsk = false">×</button>
          </div>
          <p class="finish-score"><span>{{ homeLabel }}</span> <b>{{ boardScore }}</b> <span>{{ awayLabel }}</span></p>
          <p v-if="refereePenaltyCaption(match)" class="pens">{{ refereePenaltyCaption(match) }}</p>
          <p v-if="finishExtra" class="hint">{{ finishExtra }}</p>
          <p class="hint">Проверьте счёт и протокол.</p>
          <p class="hint">Действие завершит матч.</p>
          <p v-if="finishPending" class="hint" role="status">Сохраняем…</p>
          <p v-if="finishError" ref="finishAlert" class="form-error" role="alert" tabindex="-1">{{ finishError }}</p>
          <button class="btn danger finish-go" type="button" :disabled="finishPending" @click="confirmFinish">
            {{ finishPending ? 'Сохраняем…' : 'Завершить матч' }}
          </button>
          <button class="btn secondary finish-back" type="button" data-initial-focus :disabled="finishPending" @click="finishAsk = false">Вернуться к протоколу</button>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.pad { min-width: 0; max-width: 100%; display: grid; gap: 16px; }
.back { color: var(--navy); font-weight: 700; font-size: 0.9rem; min-height: 44px; display: inline-flex; align-items: center; }
.scoreboard {
  background: #001433;
  color: #fff;
  border: 0;
  border-radius: 22px;
  display: grid;
  gap: 0.45rem;
  justify-items: center;
  text-align: center;
}
.kicker { margin: 0; color: #d5e6f5; font-weight: 800; letter-spacing: 0.04em; }
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 0.6rem;
  align-items: center;
}
.club {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-family: var(--font-display);
}
.club span { min-width: 0; overflow-wrap: break-word; }
.club.away { justify-content: flex-end; text-align: right; }
.mid { display: grid; justify-items: center; gap: 0.15rem; }
.score { font-size: clamp(1.8rem, 5vw, 2.6rem); font-weight: 800; color: #fff; font-variant-numeric: tabular-nums; }
.pens { margin: 0; color: #d5e6f5; font-size: 0.85rem; }
.clock {
  font-family: var(--font-display);
  font-size: clamp(2rem, 7vw, 3.2rem);
  font-variant-numeric: tabular-nums;
  color: #fff;
  line-height: 1;
}
.clock.expired { color: #ffd0c8; }
.status-line { margin: 0; color: #d5e6f5; font-size: 0.85rem; }
.held { margin: 0; color: #fff; }
.layout { display: grid; gap: 16px; min-width: 0; }
.block { min-width: 0; display: grid; gap: 0.65rem; align-content: start; }
.block h2 { margin: 0; font-size: 0.95rem; letter-spacing: 0.06em; }
.flow, .events { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.flow .btn, .events .btn, .finish .btn, .pick { min-height: 44px; }
.events .goal { background: #4cb4e5; color: #fff; }
.events .yellow { background: #fff; color: #001433; border: 1px solid #c5d4e4; }
.hint { margin: 0; color: var(--muted); font-size: 0.85rem; }
.side { display: grid; gap: 16px; min-width: 0; }
.keeper { display: grid; gap: 0.4rem; }
.keeper h3 { margin: 0; font-size: 0.95rem; }
.btn.pick { justify-content: flex-start; text-align: left; white-space: normal; height: auto; }
.btn.on { background: var(--navy); color: #fff; border-color: var(--navy); }
.finish { background: #fff1ee; border-color: #f3c7c0; }
.finish h2, .finish p { color: #b4402f; }
.finish .btn { background: #fff; color: #001433; justify-self: start; }
.proto {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.6rem;
  align-items: center;
  padding: 0.55rem 0;
  border-top: 1px solid var(--line);
  min-width: 0;
}
.proto .who { min-width: 0; overflow-wrap: anywhere; }
.proto.voided { opacity: 0.45; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; }
.boot { display: grid; gap: 0.55rem; }
.bar { display: block; height: 14px; border-radius: 8px; }
.bar.short { width: 62%; }
.state { display: grid; justify-items: start; gap: 0.45rem; }
.state.flat { justify-items: start; padding: 0; border: 0; background: transparent; box-shadow: none; }
.state.slim { margin-bottom: 0; }
.pen-line { margin: 0; font-weight: 800; color: var(--navy); }
.kick-row { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.kick-list { margin: 0; padding-left: 1.2rem; }
.pad :focus-visible { outline: 2px solid #4cb4e5; outline-offset: 2px; }
@media (min-width: 1000px) {
  .layout { grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); align-items: start; }
  .side { grid-column: 2; grid-row: 1 / span 6; }
  .finish { grid-column: 1; }
}
@media (max-width: 719px) {
  .sides { gap: 0.35rem; }
  .score { font-size: 1.35rem; }
  .clock { font-size: 1.65rem; }
  .club,
  .club.away {
    flex-direction: column;
    justify-content: flex-start;
    align-items: center;
    text-align: center;
    gap: 0.2rem;
  }
  .club :deep(.crest), .club :deep(svg), .club :deep(img) { width: 22px; height: 22px; }
}
.overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 14px;
  background: rgba(0, 32, 91, 0.46);
  overflow: auto;
}
.popover {
  width: min(420px, calc(100vw - 28px));
  max-height: min(86vh, 720px);
  overflow: auto;
  background: #fff;
  border-radius: 22px;
  padding: 1.05rem 1rem 1.15rem;
  box-shadow: 0 18px 40px rgba(0, 16, 46, 0.28);
  display: grid;
  gap: 0.55rem;
}
.dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; }
.dialog-head h2 { margin: 0; color: #001433; font-size: 1.15rem; }
.icon-x {
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  color: #24344d;
  font-size: 1.4rem;
  cursor: pointer;
}
.pick-grid, .chip-col, .sheet-nav { display: grid; gap: 0.5rem; }
.sheet-nav { grid-template-columns: 1fr 1fr; }
.finish-score { margin: 0; font-weight: 800; color: #001433; overflow-wrap: anywhere; }
.finish-go, .finish-back { width: 100%; min-height: 44px; }
.finish-go { background: #c4473a; color: #fff; }
@media (max-width: 719px) {
  .popover { width: min(347px, calc(100vw - 28px)); }
}
</style>
