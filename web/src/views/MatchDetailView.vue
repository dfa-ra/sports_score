<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventDetail, eventLabel, formatClock, formatWhen, labelOf, periodLabel, playerTag } from '../lib/format'
import { registeredName } from '../lib/playerTitle'
import { buildPeriodBlocks, compareMatchEvents, eventMinute, longKickoff, matchStateLabel } from '../lib/match'
import { apiError } from '../lib/errors'
import { loadAllReferees, refereeTitle } from '../lib/referees'
import { useMatchClock } from '../lib/useMatchClock'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import FutsalBoard from '../components/FutsalBoard.vue'
import SquadPlayerSelect from '../components/SquadPlayerSelect.vue'
import MatchGoalAlerts from '../components/MatchGoalAlerts.vue'
import MatchLineupBoard from '../components/MatchLineupBoard.vue'
import MatchShareButton from '../components/MatchShareButton.vue'
import TeamCrest from '../components/TeamCrest.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const referees = ref<any[]>([])
const lineups = ref<any>(null)
const homeForm = ref<any[]>([])
const awayForm = ref<any[]>([])
const allMatches = ref<any[]>([])
const tournament = ref<any>(null)
const me = ref<any>(null)
const users = ref<any[]>([])
const refereeId = ref('')
const protocolType = ref<'GOAL' | 'YELLOW_CARD' | 'RED_CARD'>('GOAL')
const protocolTeamId = ref('')
const protocolPlayerId = ref('')
const protocolMinute = ref(1)
const homeRoster = ref<any[]>([])
const awayRoster = ref<any[]>([])
const minuteDrafts = ref<Record<string, number>>({})
const playerDrafts = ref<Record<string, string>>({})
const assistDrafts = ref<Record<string, string>>({})
const motmPlayerId = ref('')
function crestForViewport() {
  return window.innerWidth <= 719 ? 72 : 96
}

const crestSize = ref(crestForViewport())
const tab = ref<'overview' | 'lineups' | 'protocol'>('overview')
const connected = ref(false)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const teams = useTeamDirectory()
const fav = useFavorites()
const { remaining, expired, cap } = useMatchClock(match)
let client: Client | null = null

const timeline = computed(() =>
  [...events.value]
    .filter((event) => !event.voided)
    .sort(compareMatchEvents),
)
const headToHead = computed(() => {
  if (!match.value) return []
  const a = match.value.homeTeamId
  const b = match.value.awayTeamId
  return allMatches.value
    .filter((row) =>
      (row.status === 'FINISHED' || row.status === 'CANCELLED')
      && row.id !== match.value.id
      && ((row.homeTeamId === a && row.awayTeamId === b) || (row.homeTeamId === b && row.awayTeamId === a))
    )
    .slice()
    .sort((x, y) => String(y.scheduledAt || '').localeCompare(String(x.scheduledAt || '')))
    .slice(0, 5)
})

function recentLine(row: any, teamId: string) {
  const opponentId = row.homeTeamId === teamId ? row.awayTeamId : row.homeTeamId
  const own = row.homeTeamId === teamId ? row.homeScore : row.awayScore
  const theirs = row.homeTeamId === teamId ? row.awayScore : row.homeScore
  return `${own}:${theirs} · ${teams.fullName(opponentId)} · ${formatWhen(row.scheduledAt)}`
}
const periodBlocks = computed(() => {
  if (!match.value) return []
  return buildPeriodBlocks(events.value, match.value).map((block) => ({
    ...block,
    label: periodLabel(block.period, match.value.sportCode, match.value.periodCount),
  }))
})

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

const playerOfTheMatchName = computed(() => registeredName(match.value?.playerOfTheMatch))

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
  const minutes: Record<string, number> = {}
  const players: Record<string, string> = {}
  const assists: Record<string, string> = {}
  for (const event of events.value) {
    minutes[event.id] = eventMinute(event.gameTime)
    players[event.id] = event.playerId || ''
    assists[event.id] = event.secondaryPlayerId && event.secondaryPlayerId !== event.playerId
      ? event.secondaryPlayerId
      : ''
  }
  minuteDrafts.value = minutes
  playerDrafts.value = players
  assistDrafts.value = assists
}

function onScorerChange(eventId: string, playerId: string) {
  if (assistDrafts.value[eventId] === playerId) assistDrafts.value[eventId] = ''
}

function applyCrest() {
  crestSize.value = crestForViewport()
}

watch(protocolPlayers, (players) => {
  if (!players.some((player) => player.playerId === protocolPlayerId.value)) {
    protocolPlayerId.value = players[0]?.playerId || ''
  }
})

function isCaptainOf(teamId?: string) {
  if (!me.value?.id || !teamId) return false
  const side = teamId === match.value?.homeTeamId ? lineups.value?.home : lineups.value?.away
  return side?.captainId === me.value.id
}

async function load() {
  const id = route.params.id
  await teams.load()
  const [m, e, r, l] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
    api.get(`/matches/${id}/referees`),
    api.get(`/matches/${id}/lineups`),
  ])
  match.value = m.data
  motmPlayerId.value = m.data.playerOfTheMatch?.playerId || ''
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
  try {
    const [hf, af, games] = await Promise.all([
      api.get(`/teams/${m.data.homeTeamId}/form`, { params: { limit: 5 } }),
      api.get(`/teams/${m.data.awayTeamId}/form`, { params: { limit: 5 } }),
      api.get('/matches', { params: { size: 100, sort: 'scheduledAt,desc' } }),
    ])
    homeForm.value = hf.data
    awayForm.value = af.data
    allMatches.value = games.data.content ?? []
  } catch {
    homeForm.value = []
    awayForm.value = []
    allMatches.value = []
  }
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
    if (!refereeId.value && users.value[0]) refereeId.value = users.value[0].id
  }
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
  pending.value = true
  try {
    await api.patch(`/admin/matches/${match.value.id}/events/${event.id}`, {
      minute: Number(minuteDrafts.value[event.id] ?? 0),
      playerId,
      secondaryPlayerId: assistDrafts.value[event.id] || null,
      updatePlayers: true,
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
  pending.value = true
  try {
    await api.patch(`/admin/matches/${match.value.id}/events/${event.id}`, {
      minute: Number(minuteDrafts.value[event.id] ?? 0),
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
})

onUnmounted(() => {
  window.removeEventListener('resize', applyCrest)
  client?.deactivate()
})
</script>

<template>
  <MatchGoalAlerts headless />
  <section v-if="match" class="stack">
    <RouterLink class="league-bar" to="/table">
      {{ tournament?.name || 'Матч' }}
      <span>›</span>
    </RouterLink>

    <div class="board" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <p class="when">{{ longKickoff(match.scheduledAt) }}</p>
      <div class="club">
        <button
          class="star"
          type="button"
          :class="{ on: fav.hasTeam(match.homeTeamId) }"
          :aria-label="teams.fullName(match.homeTeamId)"
          @click="fav.toggleTeam(match.homeTeamId)"
        >★</button>
        <RouterLink class="who" :to="`/teams/${match.homeTeamId}`">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="teams.fullName(match.homeTeamId)" :size="crestSize" />
          <strong>{{ teams.fullName(match.homeTeamId) }}</strong>
        </RouterLink>
      </div>
      <div class="center">
        <p class="score">{{ match.homeScore }} - {{ match.awayScore }}</p>
        <p v-if="playerOfTheMatchName" class="motm">
          <span>Игрок матча</span>
          <RouterLink :to="`/players/${match.playerOfTheMatch.playerId}`">{{ playerOfTheMatchName }}</RouterLink>
        </p>
        <p v-if="match.status === 'LIVE' || match.status === 'PAUSED'" class="clock" :class="{ expired }">
          {{ formatClock(remaining) }} · {{ periodLabel(match.period, match.sportCode, match.periodCount) }}
        </p>
        <p v-else-if="match.status === 'SCHEDULED'" class="muted clock-note">
          {{ match.periodCount }} × {{ formatClock(cap) }}
        </p>
      </div>
      <div class="club away">
        <RouterLink class="who" :to="`/teams/${match.awayTeamId}`">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="teams.fullName(match.awayTeamId)" :size="crestSize" />
          <strong>{{ teams.fullName(match.awayTeamId) }}</strong>
        </RouterLink>
        <button
          class="star"
          type="button"
          :class="{ on: fav.hasTeam(match.awayTeamId) }"
          @click="fav.toggleTeam(match.awayTeamId)"
        >★</button>
      </div>
      <div class="under">
        <p class="state">{{ matchStateLabel(match.status) }}</p>
        <MatchShareButton
          :home-name="teams.fullName(match.homeTeamId)"
          :away-name="teams.fullName(match.awayTeamId)"
          :home-score="match.homeScore"
          :away-score="match.awayScore"
        />
      </div>
    </div>

    <p v-if="match.venue?.trim()" class="venue-line">{{ match.venue.trim() }}</p>

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
      <h2>Протокол</h2>
      <p class="muted">Гол, жёлтая или красная. У гола можно сменить забившего и ассистента: пас не обязателен, его можно убрать. Минута — от начала матча, как её увидит зритель: во втором тайме 2×15 это 16' и дальше, не время на табло. Зрители видят счёт без убранных голов.</p>
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
          <input v-model.number="protocolMinute" type="number" min="0" max="200" required />
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
          <span class="field player">Ассистент
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
          <input v-model.number="minuteDrafts[ev.id]" type="number" min="0" max="200" />
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
      <form class="stack" @submit.prevent="assignReferee">
        <label class="field">Назначить судью
          <select v-model="refereeId" required>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ refereeTitle(u) }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending || !users.length">Назначить</button>
      </form>
      <p v-if="!users.length" class="muted">Сначала поставьте кому-то роль судьи в админке.</p>
    </AdminOnly>

    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">Обзор</button>
      <button type="button" :class="{ on: tab === 'lineups' }" @click="tab = 'lineups'">Составы</button>
      <button type="button" :class="{ on: tab === 'protocol' }" @click="tab = 'protocol'">Протокол</button>
    </div>

    <div v-if="tab === 'overview'" class="stack">
      <FutsalBoard
        :match="match"
        :events="events"
        :home-label="teams.fullName(match.homeTeamId)"
        :away-label="teams.fullName(match.awayTeamId)"
      />
      <div class="sheet">
        <template v-if="periodBlocks.length">
          <section v-for="block in periodBlocks" :key="block.period">
            <div class="half-head">
              <span>{{ block.label }}</span>
              <span>{{ block.score }}</span>
            </div>
            <div
              v-for="ev in block.items"
              :key="ev.id"
              class="ev"
              :class="ev.home ? 'home' : 'away'"
            >
              <span class="who-ev">
                <b>{{ playerTag(registeredName(ev), ev.playerJersey) || labelOf(eventLabel, ev.eventType) }}</b>
                <span v-if="ev.eventType === 'GOAL' && registeredName(ev, 'secondary')" class="line">
                  пас {{ playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey) }}
                </span>
                <span v-if="ev.scoreline" class="line">{{ ev.scoreline }}</span>
              </span>
              <i class="mark" :class="ev.eventType.toLowerCase()" />
              <em>{{ eventMinute(ev.gameTime) }}'</em>
            </div>
          </section>
        </template>
        <p v-else class="empty-line">Событий нет.</p>
      </div>
      <div class="stack recent">
        <div class="panel">
          <h2>Последние игры {{ teams.fullName(match.homeTeamId) }}</h2>
          <p v-for="f in homeForm" :key="f.id" class="muted recent-line">
            <TeamCrest
              :src="teams.logo(f.homeTeamId === match.homeTeamId ? f.awayTeamId : f.homeTeamId)"
              :name="teams.fullName(f.homeTeamId === match.homeTeamId ? f.awayTeamId : f.homeTeamId)"
              :size="16"
            />
            {{ recentLine(f, match.homeTeamId) }}
          </p>
          <p v-if="!homeForm.length" class="muted">Пока нет сыгранных матчей</p>
        </div>
        <div class="panel">
          <h2>Последние игры {{ teams.fullName(match.awayTeamId) }}</h2>
          <p v-for="f in awayForm" :key="f.id" class="muted recent-line">
            <TeamCrest
              :src="teams.logo(f.homeTeamId === match.awayTeamId ? f.awayTeamId : f.homeTeamId)"
              :name="teams.fullName(f.homeTeamId === match.awayTeamId ? f.awayTeamId : f.homeTeamId)"
              :size="16"
            />
            {{ recentLine(f, match.awayTeamId) }}
          </p>
          <p v-if="!awayForm.length" class="muted">Пока нет сыгранных матчей</p>
        </div>
        <div class="panel">
          <h2>Очные встречи</h2>
          <p v-for="f in headToHead" :key="f.id" class="muted recent-line">
            <TeamCrest :src="teams.logo(f.homeTeamId)" :name="teams.fullName(f.homeTeamId)" :size="16" />
            {{ teams.fullName(f.homeTeamId) }} {{ f.homeScore }}:{{ f.awayScore }} {{ teams.fullName(f.awayTeamId) }}
            <TeamCrest :src="teams.logo(f.awayTeamId)" :name="teams.fullName(f.awayTeamId)" :size="16" />
            · {{ formatWhen(f.scheduledAt) }}
          </p>
          <p v-if="!headToHead.length" class="muted">Пока не играли друг с другом</p>
        </div>
      </div>
      <div v-if="referees.length || auth.canOfficiate" class="panel stack">
        <h2>Бригада</h2>
        <p v-for="r in referees" :key="r.id" class="muted">Судья {{ registeredName(r) || r.email || r.refereeId }}</p>
        <p v-if="!referees.length" class="muted">Судья не назначен.</p>
        <RouterLink v-if="auth.canOfficiate" class="btn secondary" :to="`/referee/matches/${match.id}`">Открыть пульт</RouterLink>
      </div>
    </div>

    <div v-else-if="tab === 'lineups'" class="grid lineups">
      <div class="panel">
        <MatchLineupBoard
          :side="lineups?.home"
          :editable="auth.canManageLeague || auth.canOfficiate || isCaptainOf(match.homeTeamId)"
          :pending="pending"
          @save="saveLineup"
        />
      </div>
      <div class="panel">
        <MatchLineupBoard
          :side="lineups?.away"
          :editable="auth.canManageLeague || auth.canOfficiate || isCaptainOf(match.awayTeamId)"
          :pending="pending"
          @save="saveLineup"
        />
      </div>
    </div>

    <div v-else class="panel">
      <h2>Протокол</h2>
      <p v-if="!timeline.length" class="muted">Событий нет.</p>
      <ul class="timeline">
        <li v-for="ev in timeline" :key="ev.id">
          <span class="t">{{ eventMinute(ev.gameTime) }}'</span>
          <div>
            <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
            <p class="muted">{{ eventDetail(ev) }} · {{ periodLabel(ev.period, match.sportCode, match.periodCount) }}</p>
          </div>
        </li>
      </ul>
    </div>

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
.state { text-transform: uppercase; letter-spacing: 0.06em; font-weight: 800; }
.score {
  margin: 0.15rem 0;
  font-size: clamp(2rem, 5.4vw, 2.75rem);
  font-weight: 800;
  line-height: 0.95;
  color: var(--navy);
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
.motm a {
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
.recent-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0.25rem 0;
}
.protocol-form { margin: 0.4rem 0 0.8rem; }
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
</style>
