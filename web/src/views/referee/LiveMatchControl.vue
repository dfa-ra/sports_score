<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../../api/client'
import { eventDetail, eventLabel, formatClock, labelOf, periodLabel, playerTag } from '../../lib/format'
import { registeredName } from '../../lib/playerTitle'
import { finishConfirmQuestion, protocolMinute, protocolScore, publicScore, refereeBoardScore } from '../../lib/match'
import { apiError } from '../../lib/errors'
import { useMatchClock } from '../../lib/useMatchClock'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import { useAuthStore } from '../../stores/auth'
import AnalystAssignForm from '../../components/AnalystAssignForm.vue'
import FutsalBoard from '../../components/FutsalBoard.vue'
import MatchStageNote from '../../components/MatchStageNote.vue'
import StatusBadge from '../../components/StatusBadge.vue'
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
const pending = ref(false)
const voidingId = ref('')
const protocolDirty = ref(false)
const sheet = ref<Sheet | null>(null)
const heldClock = ref(false)
const teams = useTeamDirectory()
const { elapsed, remaining, expired, cap } = useMatchClock(match)
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

async function recordKick(teamId: string, scored: boolean) {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.post(`/referee/matches/${route.params.id}/shootout`, { teamId, scored })
    shootout.value = data
    await refreshMatch()
  } catch (reason) {
    error.value = apiError(reason, 'Удар не записался.')
  } finally {
    pending.value = false
  }
}

async function undoKick() {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.delete(`/referee/matches/${route.params.id}/shootout/last`)
    shootout.value = data
    await refreshMatch()
  } catch (reason) {
    error.value = apiError(reason, 'Последний удар не убрался.')
  } finally {
    pending.value = false
  }
}

async function action(path: string) {
  if (path === 'finish') {
    const current = match.value
    const series = shootout.value
    const halfStillOpen = remaining.value > 0 || (current?.period ?? 1) < (current?.periodCount ?? 1)
    const question = finishConfirmQuestion({
      halfStillOpen,
      homeScore: current?.homeScore,
      awayScore: current?.awayScore,
      penaltyHome: series?.penaltyHome ?? current?.penaltyHome,
      penaltyAway: series?.penaltyAway ?? current?.penaltyAway,
      decisive: !!series?.decisive,
    })
    if (!confirm(question)) return
  }
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    message.value = path === 'finish' ? 'Матч завершён.' : 'Готово.'
  } catch (e: any) {
    error.value = apiError(e, 'Действие не прошло.')
  } finally {
    pending.value = false
  }
}

function canUndo(ev: { voided?: boolean; eventType?: string }) {
  if (!live.value || ev.voided) return false
  return ev.eventType !== 'PERIOD_START' && ev.eventType !== 'PERIOD_END'
}

async function undoEvent(ev: { id: string; eventType?: string }) {
  if (pending.value || voidingId.value === ev.id) return
  const label = labelOf(eventLabel, ev.eventType)
  if (!confirm(`Убрать «${label}» из протокола? Счёт пересчитается по оставшимся голам.`)) return
  if (pending.value || voidingId.value === ev.id) return
  message.value = ''
  error.value = ''
  pending.value = true
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
    pending.value = false
    voidingId.value = ''
  }
}

async function addEvent(payload: Record<string, unknown>) {
  if (pending.value) return
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    const played = elapsed.value
    const { data } = await api.post(`/referee/matches/${route.params.id}/events`, {
      ...payload,
      ...(played > 0 ? { gameTime: played } : {}),
    })
    upsertProtocolEvent(data)
    sheet.value = null
    message.value = 'В протоколе.'
    await releaseClock()
    try {
      await reload()
    } catch {
      /* the saved event is already on the board */
    }
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось записать событие')
  } finally {
    pending.value = false
  }
}

async function openSheet(kind: EventKind) {
  if (pending.value) return
  error.value = ''
  if (!live.value) {
    error.value = 'Сначала стартуйте матч.'
    return
  }
  if (kind === 'YELLOW_CARD' || kind === 'RED_CARD') {
    await holdClock()
  }
  sheet.value = { kind, step: 'team' }
}

function recordTeamGoal(ownGoal: boolean) {
  const current = sheet.value
  if (!current?.teamId || pending.value) return
  addEvent({
    eventType: 'GOAL',
    teamId: current.teamId,
    metadata: ownGoal ? { ownGoal: true } : undefined,
  })
}

function recordTeamCard() {
  const current = sheet.value
  if (!current?.teamId || pending.value || current.kind === 'GOAL' || current.kind === 'SUBSTITUTION') return
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
  if (!current || pending.value) return
  if (!rosterOf(current.teamId).length) {
    recordTeamGoal(true)
    return
  }
  sheet.value = { ...current, ownGoal: !current.ownGoal }
}

function pickPlayer(player: any) {
  const current = sheet.value
  if (!current?.teamId || pending.value) return
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
  if (!current?.teamId || !current.player || !incomingId || pending.value) return
  addEvent({
    eventType: 'SUBSTITUTION',
    teamId: current.teamId,
    playerId: current.player.playerId,
    secondaryPlayerId: incomingId,
  })
}

function confirmGoal(assistPlayerId?: string | null, ownGoal = false) {
  if (pending.value) return
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
  if (!playerId || chosenKeeperId(teamId) === playerId) return
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.put(`/referee/matches/${route.params.id}/goalkeeper`, { teamId, playerId })
    message.value = 'Вратарь записан.'
    await reload()
  } catch (e: any) {
    error.value = apiError(e, 'Вратаря не удалось записать')
  } finally {
    pending.value = false
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

onMounted(() => {
  reload().finally(connectLive)
})
onUnmounted(() => {
  disconnectLive()
  if (!heldClock.value) return
  heldClock.value = false
  api.post(`/referee/matches/${route.params.id}/resume`).catch((reason) => {
    error.value = apiError(reason, 'Часы не запустились. Нажмите «Продолжить».')
  })
})
</script>

<template>
  <section v-if="match && !canControl" class="stack pad">
    <div class="page-title">
      <RouterLink class="back" to="/referee">← К матчам</RouterLink>
      <h1>Пульт</h1>
    </div>
    <p class="form-error">Нет доступа: вы не назначены на этот матч.</p>
  </section>
  <section v-if="match && canControl" class="stack pad">
    <div class="page-title">
      <RouterLink class="back" to="/referee">← К матчам</RouterLink>
      <h1>Пульт</h1>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <MatchStageNote variant="protocol" :match="match" />
      <StatusBadge :status="match.status" :at="match.scheduledAt ?? null" :third-place="!!match.thirdPlace" />
      <p class="period">{{ periodLabel(match.period, match.sportCode, match.periodCount) }}</p>
      <div class="clock" :class="{ expired }">{{ formatClock(remaining) }}</div>
      <p class="muted">осталось из {{ formatClock(cap) }} · прошло {{ formatClock(elapsed) }}</p>
      <p v-if="heldClock" class="form-ok">Матч на паузе, часы стоят.</p>
      <div class="sides">
        <strong class="club">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="28" />
          {{ homeLabel }}
        </strong>
        <div class="score">
          {{ refereeBoardScore(match, shootout, shootoutOpen) }}
        </div>
        <strong class="club">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="28" />
          {{ awayLabel }}
        </strong>
      </div>
    </div>

    <AnalystAssignForm
      :match-id="String(match.id)"
      :home-label="homeLabel"
      :away-label="awayLabel"
    />

    <FutsalBoard
      :match="match"
      :events="events"
      :home-label="homeLabel"
      :away-label="awayLabel"
      interactive
      :pending="pending"
      :live="live"
      @foul="(teamId) => addEvent({ eventType: 'FOUL', teamId })"
      @timeout="(teamId) => addEvent({ eventType: 'TIMEOUT', teamId })"
    />

    <div class="grid controls">
      <button class="btn large success" :disabled="pending || match.status !== 'SCHEDULED'" @click="action('start')">Старт</button>
      <button class="btn large secondary" :disabled="pending || match.status !== 'LIVE'" @click="action('pause')">Пауза</button>
      <button class="btn large secondary" :disabled="pending || match.status !== 'PAUSED'" @click="action('resume')">Продолжить</button>
      <button
        class="btn large"
        :disabled="pending || !live || (match.period ?? 1) >= match.periodCount"
        @click="action('next-period')"
      >Следующий тайм</button>
      <button
        class="btn large danger"
        :disabled="pending || !live || (shootoutOpen && !shootout?.decisive)"
        @click="action('finish')"
      >Финиш</button>
    </div>
    <p v-if="expired && live" class="form-ok">Время тайма вышло.</p>

    <div v-if="shootoutOpen" class="panel stack shootout">
      <h2>Серия пенальти</h2>
      <p class="muted">
        Ничья в плей-офф. «Финиш» откроется, когда серия определит победителя.
        Первые пять раундов — основа, дальше бьют, пока один не забьёт, а другой не промахнётся.
      </p>
      <p v-if="shootout?.kicks?.length" class="pen-line">{{ shootoutLine }}</p>
      <p class="muted">{{ turnTeamId === match.homeTeamId ? `Бьёт ${homeLabel}` : turnTeamId === match.awayTeamId ? `Бьёт ${awayLabel}` : 'Серия решена' }}</p>
      <div class="kick-row">
        <button class="btn secondary" type="button" :disabled="pending || turnTeamId !== match.homeTeamId" @click="recordKick(match.homeTeamId, true)">{{ homeLabel }}: гол</button>
        <button class="btn secondary" type="button" :disabled="pending || turnTeamId !== match.homeTeamId" @click="recordKick(match.homeTeamId, false)">{{ homeLabel }}: мимо</button>
        <button class="btn secondary" type="button" :disabled="pending || turnTeamId !== match.awayTeamId" @click="recordKick(match.awayTeamId, true)">{{ awayLabel }}: гол</button>
        <button class="btn secondary" type="button" :disabled="pending || turnTeamId !== match.awayTeamId" @click="recordKick(match.awayTeamId, false)">{{ awayLabel }}: мимо</button>
      </div>
      <button v-if="shootout?.kicks?.length" class="btn secondary" type="button" :disabled="pending" @click="undoKick">Убрать последний удар</button>
      <ol v-if="shootout?.kicks?.length" class="kick-list">
        <li v-for="kick in shootout.kicks" :key="kick.id">
          Раунд {{ kick.round }} · {{ kick.teamId === match.homeTeamId ? homeLabel : awayLabel }} · {{ kick.scored ? 'гол' : 'мимо' }}
        </li>
      </ol>
    </div>

    <div class="panel stack keepers">
      <h2>Вратари</h2>
      <p class="muted">Нажмите, кто стоял в воротах. Другой выбор заменяет первый. Если никого не нажать, останется вратарь из состава.</p>
      <div class="keeper-grid">
        <div>
          <h3>{{ homeLabel }}</h3>
          <p v-if="!keeperChoices(match.homeTeamId).length" class="muted">В составе никого.</p>
          <button
            v-for="p in keeperChoices(match.homeTeamId)"
            :key="p.playerId"
            class="btn secondary pick"
            :class="{ on: chosenKeeperId(match.homeTeamId) === p.playerId }"
            :disabled="pending"
            @click="setKeeper(match.homeTeamId, p.playerId)"
          >{{ nameOf(p) }}</button>
        </div>
        <div>
          <h3>{{ awayLabel }}</h3>
          <p v-if="!keeperChoices(match.awayTeamId).length" class="muted">В составе никого.</p>
          <button
            v-for="p in keeperChoices(match.awayTeamId)"
            :key="p.playerId"
            class="btn secondary pick"
            :class="{ on: chosenKeeperId(match.awayTeamId) === p.playerId }"
            :disabled="pending"
            @click="setKeeper(match.awayTeamId, p.playerId)"
          >{{ nameOf(p) }}</button>
        </div>
      </div>
    </div>

    <div class="grid events">
      <button class="btn large goal" :disabled="pending || !live" @click="openSheet('GOAL')">Гол</button>
      <button class="btn large yellow" :disabled="pending || !live" @click="openSheet('YELLOW_CARD')">Жёлтая</button>
      <button class="btn large danger" :disabled="pending || !live" @click="openSheet('RED_CARD')">Красная</button>
      <button class="btn large secondary" :disabled="pending || !live" @click="openSheet('SUBSTITUTION')">Замена</button>
    </div>

    <div class="panel stack">
      <h2>Протокол</h2>
      <p v-if="!protocol.length" class="muted">Событий пока нет</p>
      <div v-for="ev in protocol" :key="ev.id" class="proto" :class="{ voided: ev.voided }">
        <span v-if="protocolMinute(ev, match.periodLengthSeconds) != null" class="t">{{ protocolMinute(ev, match.periodLengthSeconds) }}'</span>
        <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
        <span>{{ eventDetail(ev) || '—' }}</span>
        <button v-if="canUndo(ev)" class="btn ghost" type="button" :disabled="pending || voidingId === ev.id" @click="undoEvent(ev)">Убрать</button>
      </div>
    </div>
    <p v-if="message" class="form-ok">{{ message }}</p>
    <p v-if="error" class="form-error">{{ error }}</p>

    <div v-if="sheet" class="overlay" @click.self="closeSheet">
      <div class="popover" role="dialog" aria-modal="true">
        <p class="step">{{ stepIndex(sheet.step) }} / {{ stepCount }}</p>
        <h2>{{ sheetTitle }}</h2>
        <p v-if="heldClock" class="form-ok">Матч на паузе, часы стоят.</p>
        <p class="muted">{{ sheetHint }}</p>

        <div v-if="sheet.step === 'team'" class="pick-grid">
          <button class="btn large secondary" :disabled="pending" @click="pickTeam(match.homeTeamId)">{{ homeLabel }}</button>
          <button class="btn large secondary" :disabled="pending" @click="pickTeam(match.awayTeamId)">{{ awayLabel }}</button>
        </div>

        <div v-else-if="sheet.step === 'player'" class="chip-col">
          <template v-if="sheet.kind === 'GOAL' && !rosterOf(sheet.teamId).length">
            <p class="muted">В заявке никого. Гол запишется на команду.</p>
            <button class="btn large" type="button" :disabled="pending" @click="recordTeamGoal(false)">Гол команды</button>
            <button class="btn large" type="button" :disabled="pending" @click="recordTeamGoal(true)">Автогол</button>
          </template>
          <template v-else-if="sheet.kind === 'SUBSTITUTION' && rosterOf(sheet.teamId).length < 2">
            <p class="muted">Для замены нужны два игрока в заявке.</p>
          </template>
          <template v-else>
            <button
              v-if="sheet.kind === 'GOAL'"
              class="btn large"
              :class="{ on: sheet.ownGoal }"
              type="button"
              :disabled="pending"
              @click="toggleOwnGoal"
            >Автогол</button>
            <p v-if="!rosterOf(sheet.teamId).length" class="muted">В заявке никого.</p>
            <button
              v-if="sheet.kind !== 'GOAL' && sheet.kind !== 'SUBSTITUTION' && !rosterOf(sheet.teamId).length"
              class="btn large"
              type="button"
              :disabled="pending"
              @click="recordTeamCard"
            >Карточка команде</button>
            <button
              v-for="p in rosterOf(sheet.teamId)"
              :key="p.playerId"
              class="btn secondary pick"
              :disabled="pending"
              @click="pickPlayer(p)"
            >{{ nameOf(p) }}</button>
          </template>
        </div>

        <div v-else-if="sheet.step === 'incoming'" class="chip-col">
          <button
            v-for="p in rosterOf(sheet.teamId).filter((x: any) => x.playerId !== sheet?.player?.playerId)"
            :key="p.playerId"
            class="btn secondary pick"
            :disabled="pending"
            @click="confirmSubstitution(p.playerId)"
          >{{ nameOf(p) }}</button>
        </div>

        <div v-else class="chip-col">
          <button class="btn large on" :disabled="pending" @click="confirmGoal(null, true)">Автогол</button>
          <button class="btn large" :disabled="pending" @click="confirmGoal(null)">Без передачи</button>
          <button
            v-for="p in rosterOf(sheet.teamId).filter((x: any) => x.playerId !== sheet?.player?.playerId)"
            :key="p.playerId"
            class="btn secondary pick"
            :disabled="pending"
            @click="confirmGoal(p.playerId)"
          >{{ nameOf(p) }}</button>
        </div>

        <div class="sheet-nav">
          <button class="btn ghost" type="button" @click="backSheet">Назад</button>
          <button class="btn ghost" type="button" @click="closeSheet">Закрыть</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 0.35rem;
  color: var(--muted);
  font-weight: 700;
  font-size: 0.85rem;
}
.scoreboard { display: grid; gap: 0.45rem; justify-items: center; text-align: center; border-radius: 26px 18px 22px 16px; }
.period { margin: 0; color: var(--accent); font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; font-size: 0.78rem; }
.clock {
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 9vw, 4rem);
  font-variant-numeric: tabular-nums;
  color: var(--text-strong);
  line-height: 1;
}
.clock.expired { color: var(--danger); }
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1rem;
  align-items: center;
}
.sides strong { font-family: var(--font-display); }
.sides strong:last-child { text-align: right; justify-content: flex-end; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
.score { font-size: clamp(1.6rem, 5vw, 2.2rem); font-weight: 800; color: var(--navy); }
.controls, .events { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
.btn.goal { background: var(--navy); color: #fff; }
.btn.yellow { background: #f5c400; color: var(--navy); border-color: #e0b200; }
h2 { font-size: 1.15rem; margin: 0 0 0.35rem; }
.proto {
  display: grid;
  grid-template-columns: 64px 90px 1fr auto;
  gap: 0.6rem;
  align-items: center;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--line);
}
.proto.voided { opacity: 0.45; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: end center;
  padding: 0.75rem;
  background: rgba(0, 32, 91, 0.42);
}
.popover {
  width: min(520px, 100%);
  max-height: min(82vh, 680px);
  overflow: auto;
  background: #fff;
  border-radius: 22px 22px 16px 16px;
  padding: 1.1rem 1.15rem 1.2rem;
  box-shadow: var(--shadow);
}
.step {
  margin: 0 0 0.35rem;
  color: var(--ice);
  font-weight: 800;
  letter-spacing: 0.08em;
  font-size: 0.72rem;
}
.pick-grid { display: grid; gap: 0.6rem; margin-top: 0.9rem; }
.chip-col { display: grid; gap: 0.45rem; margin-top: 0.9rem; }
.btn.pick { justify-content: flex-start; text-align: left; }
.btn.on { background: var(--navy); color: #fff; border-color: var(--navy); }
.keepers h3 { margin: 0 0 0.35rem; font-size: 0.95rem; }
.shootout h2 { margin: 0; }
.pen-line { margin: 0; font-weight: 800; color: var(--navy); }
.kick-row { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.kick-list { margin: 0; padding-left: 1.2rem; }
.keeper-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.keeper-grid .btn { margin-bottom: 0.35rem; }
.sheet-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 0.9rem;
}
@media (min-width: 720px) {
  .overlay { place-items: center; }
  .popover { border-radius: 20px; }
}
@media (max-width: 860px) {
  .proto { grid-template-columns: 56px 1fr; }
}
</style>
