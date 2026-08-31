<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventDetail, eventLabel, formatClock, formatWhen, initials, labelOf, periodLabel, playerTag } from '../lib/format'
import { eventMinute, longKickoff, matchStateLabel } from '../lib/match'
import { apiError } from '../lib/errors'
import { useMatchClock } from '../lib/useMatchClock'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import { useFavorites } from '../stores/favorites'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import MatchLineupBoard from '../components/MatchLineupBoard.vue'

const route = useRoute()
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
const tab = ref<'overview' | 'lineups' | 'protocol'>('overview')
const connected = ref(false)
const error = ref('')
const ok = ref('')
const pending = ref(false)
const teams = useTeamDirectory()
const fav = useFavorites()
const { remaining, expired, cap } = useMatchClock(match)
let client: Client | null = null

const timeline = computed(() => [...events.value].filter((e) => !e.voided).reverse())
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
  const visibleTypes = new Set(['GOAL', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION', 'OWN_GOAL'])
  const chrono = [...events.value]
    .filter((e) => !e.voided && visibleTypes.has(e.eventType))
    .sort((a, b) => (a.gameTime ?? 0) - (b.gameTime ?? 0) || String(a.id).localeCompare(String(b.id)))
  let home = 0
  let away = 0
  const byPeriod = new Map<number, { items: any[]; score: string }>()
  for (const ev of chrono) {
    if (ev.eventType === 'GOAL' || ev.eventType === 'OWN_GOAL') {
      if (ev.teamId === match.value.homeTeamId) home += 1
      else away += 1
    }
    const period = ev.period || 1
    const block = byPeriod.get(period) ?? { items: [], score: '0-0' }
    block.items.push({
      ...ev,
      home: ev.teamId === match.value.homeTeamId,
      scoreline: ev.eventType === 'GOAL' || ev.eventType === 'OWN_GOAL' ? `${home}-${away}` : null,
    })
    block.score = `${home}-${away}`
    byPeriod.set(period, block)
  }
  return [...byPeriod.entries()].map(([period, block]) => ({
    period,
    label: periodLabel(period, match.value.sportCode, match.value.periodCount),
    score: block.score,
    items: block.items,
  }))
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
  events.value = e.data
  referees.value = r.data
  lineups.value = l.data
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
    const { data } = await api.get('/admin/users', { params: { size: 100 } })
    users.value = (data.content ?? []).filter((u: any) => u.role === 'REFEREE')
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

onUnmounted(() => client?.deactivate())
</script>

<template>
  <section v-if="match" class="stack">
    <RouterLink class="league-bar" to="/table">
      {{ tournament?.name || 'KRONBARS' }}
      <span>›</span>
    </RouterLink>

    <div class="board" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <div class="club">
        <button
          class="star"
          type="button"
          :class="{ on: fav.hasTeam(match.homeTeamId) }"
          :aria-label="teams.fullName(match.homeTeamId)"
          @click="fav.toggleTeam(match.homeTeamId)"
        >★</button>
        <RouterLink class="who" :to="`/teams/${match.homeTeamId}`">
          <span class="crest">{{ initials(teams.name(match.homeTeamId)) }}</span>
          <strong>{{ teams.fullName(match.homeTeamId) }}</strong>
        </RouterLink>
      </div>
      <div class="center">
        <p class="when">{{ longKickoff(match.scheduledAt) }}</p>
        <p class="score">{{ match.homeScore }} - {{ match.awayScore }}</p>
        <p class="state">{{ matchStateLabel(match.status) }}</p>
        <p v-if="match.status === 'LIVE' || match.status === 'PAUSED'" class="clock" :class="{ expired }">
          {{ formatClock(remaining) }} · {{ periodLabel(match.period, match.sportCode, match.periodCount) }}
        </p>
        <p v-else-if="match.status === 'SCHEDULED'" class="muted clock-note">
          {{ match.periodCount }} × {{ formatClock(cap) }}
        </p>
      </div>
      <div class="club away">
        <RouterLink class="who" :to="`/teams/${match.awayTeamId}`">
          <span class="crest">{{ initials(teams.name(match.awayTeamId)) }}</span>
          <strong>{{ teams.fullName(match.awayTeamId) }}</strong>
        </RouterLink>
        <button
          class="star"
          type="button"
          :class="{ on: fav.hasTeam(match.awayTeamId) }"
          @click="fav.toggleTeam(match.awayTeamId)"
        >★</button>
      </div>
    </div>

    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">Обзор</button>
      <button type="button" :class="{ on: tab === 'lineups' }" @click="tab = 'lineups'">Составы</button>
      <button type="button" :class="{ on: tab === 'protocol' }" @click="tab = 'protocol'">Протокол</button>
    </div>

    <div v-if="tab === 'overview'" class="stack">
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
                <b>{{ playerTag(ev.playerName, ev.playerJersey) || labelOf(eventLabel, ev.eventType) }}</b>
                <span v-if="ev.scoreline" class="line">{{ ev.scoreline }}</span>
              </span>
              <i class="mark" :class="ev.eventType.toLowerCase()" />
              <em>{{ eventMinute(ev.gameTime) }}'</em>
            </div>
          </section>
        </template>
        <p v-else class="empty-line">Пока ни гола, ни карточки.</p>
      </div>
      <div class="stack recent">
        <div class="panel">
          <h2>Последние игры {{ teams.fullName(match.homeTeamId) }}</h2>
          <p v-for="f in homeForm" :key="f.id" class="muted">{{ recentLine(f, match.homeTeamId) }}</p>
          <p v-if="!homeForm.length" class="muted">Пока нет сыгранных матчей</p>
        </div>
        <div class="panel">
          <h2>Последние игры {{ teams.fullName(match.awayTeamId) }}</h2>
          <p v-for="f in awayForm" :key="f.id" class="muted">{{ recentLine(f, match.awayTeamId) }}</p>
          <p v-if="!awayForm.length" class="muted">Пока нет сыгранных матчей</p>
        </div>
        <div class="panel">
          <h2>Очные встречи</h2>
          <p v-for="f in headToHead" :key="f.id" class="muted">
            {{ teams.fullName(f.homeTeamId) }} {{ f.homeScore }}:{{ f.awayScore }} {{ teams.fullName(f.awayTeamId) }} · {{ formatWhen(f.scheduledAt) }}
          </p>
          <p v-if="!headToHead.length" class="muted">Пока не играли друг с другом</p>
        </div>
      </div>
      <div v-if="referees.length || auth.canOfficiate" class="panel stack">
        <h2>Бригада</h2>
        <p v-for="r in referees" :key="r.id" class="muted">Судья {{ r.refereeEmail || r.refereeId }}</p>
        <p v-if="!referees.length" class="muted">Судья ещё не назначен — свисток сам себя не найдёт.</p>
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
      <p class="muted">Как у SofaScore, только без рекламы буклинии и с характером общаги.</p>
      <ul class="timeline">
        <li v-for="ev in timeline" :key="ev.id">
          <span class="t">{{ formatClock(ev.gameTime) }}</span>
          <div>
            <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
            <p class="muted">{{ eventDetail(ev) }} · {{ periodLabel(ev.period, match.sportCode, match.periodCount) }}</p>
          </div>
        </li>
      </ul>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(match.id)" label="Скопировать id матча" />
      <form class="stack" @submit.prevent="assignReferee">
        <label class="field">Назначить судью
          <select v-model="refereeId" required>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.email }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="pending || !users.length">Назначить</button>
      </form>
      <p v-if="!users.length" class="muted">Сначала поставьте кому-то роль судьи в админке.</p>
    </AdminOnly>
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
.board {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.6rem;
  align-items: center;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.9rem 0.7rem 1rem;
}
.club { display: grid; justify-items: center; gap: 0.35rem; min-width: 0; }
.who {
  display: grid;
  justify-items: center;
  gap: 0.35rem;
  color: inherit;
  text-align: center;
  min-width: 0;
}
.crest {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #fff;
  color: var(--navy);
  font-weight: 800;
  box-shadow: var(--shadow);
}
.who strong {
  font-size: 0.88rem;
  line-height: 1.2;
}
.star {
  border: 0;
  background: transparent;
  color: #c5ced8;
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0;
}
.star.on { color: var(--ice); }
.center { text-align: center; }
.when, .state, .clock-note { margin: 0; font-size: 0.78rem; color: var(--muted); }
.state { text-transform: uppercase; letter-spacing: 0.06em; font-weight: 800; }
.score {
  margin: 0.15rem 0;
  font-size: clamp(1.8rem, 6vw, 2.6rem);
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
.lineups { grid-template-columns: 1fr 1fr; gap: 1rem; }
h2 { font-size: 1.2rem; margin-bottom: 0.35rem; }
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
  .who strong { font-size: 0.78rem; }
  .crest { width: 40px; height: 40px; }
  .lineups { grid-template-columns: 1fr; }
  .board { padding: 0.75rem 0.5rem 0.85rem; gap: 0.35rem; }
}
</style>
