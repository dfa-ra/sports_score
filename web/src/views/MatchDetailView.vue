<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { eventDetail, eventLabel, formatClock, formatWhen, initials, labelOf, periodLabel, playerTag } from '../lib/format'
import { apiError } from '../lib/errors'
import { useMatchClock } from '../lib/useMatchClock'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'
import MatchLineupBoard from '../components/MatchLineupBoard.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const auth = useAuthStore()
const match = ref<any>(null)
const events = ref<any[]>([])
const referees = ref<any[]>([])
const lineups = ref<any>(null)
const homeForm = ref<any[]>([])
const awayForm = ref<any[]>([])
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
const { elapsed, remaining, expired, cap } = useMatchClock(match)
let client: Client | null = null

const timeline = computed(() => [...events.value].filter((e) => !e.voided).reverse())
const goals = computed(() => timeline.value.filter((e) => e.eventType === 'GOAL'))
const homeGoals = computed(() => goals.value.filter((e) => e.teamId === match.value?.homeTeamId))
const awayGoals = computed(() => goals.value.filter((e) => e.teamId === match.value?.awayTeamId))

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
    const [hf, af] = await Promise.all([
      api.get(`/teams/${m.data.homeTeamId}/form`, { params: { limit: 5 } }),
      api.get(`/teams/${m.data.awayTeamId}/form`, { params: { limit: 5 } }),
    ])
    homeForm.value = hf.data
    awayForm.value = af.data
  } catch {
    homeForm.value = []
    awayForm.value = []
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
    <div class="page-title">
      <p class="eyebrow">{{ tournament?.name || 'Студенческая сетка' }}</p>
      <p class="muted">{{ connected ? 'Live, как у Flashscore, только с общаги' : 'Подключаемся к трансляции…' }} · {{ formatWhen(match.scheduledAt) }}</p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <StatusBadge :status="match.status" />
      <p class="period">{{ periodLabel(match.period, match.sportCode, match.periodCount) }}</p>
      <div class="clock" :class="{ expired }">
        {{ match.status === 'SCHEDULED' ? formatClock(cap) : formatClock(remaining) }}
      </div>
      <p class="muted">
        <template v-if="match.status === 'SCHEDULED'">до старта · {{ match.periodCount }} × {{ formatClock(cap) }}</template>
        <template v-else>осталось · прошло {{ formatClock(elapsed) }}</template>
      </p>
      <div class="sides">
        <div class="team">
          <span class="crest">{{ initials(teams.name(match.homeTeamId)) }}</span>
          <strong>{{ teams.fullName(match.homeTeamId) }}</strong>
          <p class="scorers">
            <span v-for="g in homeGoals" :key="g.id">{{ playerTag(g.playerName, g.playerJersey) || 'гол' }} {{ formatClock(g.gameTime) }}</span>
            <span v-if="!homeGoals.length" class="muted">ещё без гола</span>
          </p>
        </div>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <div class="team away">
          <span class="crest">{{ initials(teams.name(match.awayTeamId)) }}</span>
          <strong>{{ teams.fullName(match.awayTeamId) }}</strong>
          <p class="scorers">
            <span v-for="g in awayGoals" :key="g.id">{{ playerTag(g.playerName, g.playerJersey) || 'гол' }} {{ formatClock(g.gameTime) }}</span>
            <span v-if="!awayGoals.length" class="muted">ещё без гола</span>
          </p>
        </div>
      </div>
    </div>

    <div class="tabs">
      <button class="btn secondary" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">Обзор</button>
      <button class="btn secondary" :class="{ on: tab === 'lineups' }" @click="tab = 'lineups'">Составы</button>
      <button class="btn secondary" :class="{ on: tab === 'protocol' }" @click="tab = 'protocol'">Протокол</button>
    </div>

    <div v-if="tab === 'overview'" class="stack">
      <div class="panel">
        <h2>Лента</h2>
        <p v-if="!timeline.length" class="muted" style="margin-top:0.7rem">Пока ни гола, ни карточки. Тишина перед взрывом, как в очереди в столовку.</p>
        <ul v-else class="timeline">
          <li v-for="ev in timeline.slice(0, 12)" :key="ev.id">
            <span class="t">{{ formatClock(ev.gameTime) }}</span>
            <div>
              <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
              <p class="muted">{{ eventDetail(ev) || periodLabel(ev.period, match.sportCode, match.periodCount) }}</p>
            </div>
          </li>
        </ul>
      </div>
      <div class="grid two">
        <div class="panel">
          <h2>Форма хозяев</h2>
          <p v-for="f in homeForm" :key="f.id" class="muted">{{ f.homeScore }}:{{ f.awayScore }} · {{ formatWhen(f.scheduledAt) }}</p>
          <p v-if="!homeForm.length" class="muted">Ещё нет пяти матчей.</p>
        </div>
        <div class="panel">
          <h2>Форма гостей</h2>
          <p v-for="f in awayForm" :key="f.id" class="muted">{{ f.homeScore }}:{{ f.awayScore }} · {{ formatWhen(f.scheduledAt) }}</p>
          <p v-if="!awayForm.length" class="muted">Ещё нет пяти матчей.</p>
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
.scoreboard { display: grid; gap: 0.45rem; justify-items: center; text-align: center; border-radius: 28px 18px 24px 16px; }
.period { margin: 0; color: var(--accent); font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; font-size: 0.75rem; }
.clock {
  font-family: var(--font-display);
  font-size: clamp(2.2rem, 6vw, 3.4rem);
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
  align-items: start;
  margin-top: 0.4rem;
}
.team { display: grid; gap: 0.35rem; justify-items: center; }
.team.away { justify-items: center; }
.crest {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px 11px 13px 10px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
}
.sides strong { font-family: var(--font-display); font-size: 1.05rem; }
.scorers { display: grid; gap: 0.15rem; font-size: 0.78rem; color: var(--muted); }
.tabs { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn.on { background: var(--accent); color: var(--navy); border-color: transparent; }
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
  border-radius: 13px 11px 14px 10px;
  background: #f6f9fc;
}
.t { color: var(--accent); font-variant-numeric: tabular-nums; font-size: 0.85rem; padding-top: 0.15rem; }
@media (max-width: 860px) {
  .sides { grid-template-columns: 1fr auto 1fr; gap: 0.45rem; }
  .sides strong { font-size: 0.82rem; }
  .lineups { grid-template-columns: 1fr; }
  .scoreboard { border-radius: 14px; padding: 0.9rem; }
  .clock { font-size: 1.6rem; }
}
</style>
