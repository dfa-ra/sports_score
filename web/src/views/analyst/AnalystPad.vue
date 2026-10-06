<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../../api/client'
import { apiError } from '../../lib/errors'
import { formatClock, periodLabel } from '../../lib/format'
import { useMatchClock } from '../../lib/useMatchClock'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import { useAuthStore } from '../../stores/auth'
import StatusBadge from '../../components/StatusBadge.vue'
import TeamCrest from '../../components/TeamCrest.vue'

type StatId = 'SHOT_OFF' | 'SHOT_ON_TARGET' | 'SAVE' | 'CORNER' | 'FOUL' | 'FREE_KICK' | 'KICK_IN' | 'WOODWORK'
type Side = 'HOME' | 'AWAY' | 'PAUSED'

const STATS: { id: StatId; label: string }[] = [
  { id: 'SHOT_OFF', label: 'Удар мимо' },
  { id: 'SHOT_ON_TARGET', label: 'В створ' },
  { id: 'SAVE', label: 'Сейв' },
  { id: 'CORNER', label: 'Угловой' },
  { id: 'FOUL', label: 'Фол' },
  { id: 'FREE_KICK', label: 'Штрафной' },
  { id: 'KICK_IN', label: 'Аут' },
  { id: 'WOODWORK', label: 'Каркас' },
]

const route = useRoute()
const auth = useAuthStore()
const teams = useTeamDirectory()
const match = ref<any>(null)
const stats = ref<any>(null)
const error = ref('')
const pending = ref(false)
const { remaining, cap } = useMatchClock(match)
let timer = 0
let matchTimer = 0
let client: Client | null = null

const clockRunning = computed(() => match.value?.status === 'LIVE' || match.value?.status === 'PAUSED')
const clockFinished = computed(() => match.value?.status === 'FINISHED')
const clockSeconds = computed(() => {
  if (clockFinished.value) return 0
  if (!clockRunning.value) return cap.value
  return remaining.value
})
const clockCaption = computed(() => {
  if (clockFinished.value || !match.value) return ''
  if (!clockRunning.value) return 'Не начался'
  return periodLabel(match.value.period, match.value.sportCode, match.value.periodCount)
})
const clockText = computed(() => {
  const [minutes, seconds] = formatClock(clockSeconds.value).split(':')
  return `${(minutes || '0').padStart(2, '0')}:${seconds || '00'}`
})
let clockStamp = 0

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))
const possessionSide = computed(() => stats.value?.possessionSide as Side | null)
const tracked = computed(() => !!stats.value?.possessionTracked)

function countOf(side: 'home' | 'away', stat: StatId) {
  const team = stats.value?.[side]
  if (!team) return 0
  if (stat === 'SHOT_OFF') return Math.max(0, (team.shots ?? 0) - (team.shotsOnTarget ?? 0))
  if (stat === 'SHOT_ON_TARGET') return team.shotsOnTarget ?? 0
  if (stat === 'SAVE') return team.saves ?? 0
  if (stat === 'CORNER') return team.corners ?? 0
  if (stat === 'FOUL') return team.fouls ?? 0
  if (stat === 'FREE_KICK') return team.freeKicks ?? 0
  if (stat === 'KICK_IN') return team.kickIns ?? 0
  return team.woodwork ?? 0
}

function teamId(side: 'home' | 'away') {
  return side === 'home' ? match.value?.homeTeamId : match.value?.awayTeamId
}

async function reload() {
  await teams.load()
  const id = route.params.id
  const [m, s] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/analyst-stats`),
  ])
  match.value = m.data
  stats.value = s.data
}

function applyLiveClock(live: any) {
  if (!match.value || !live) return
  clockStamp = Date.now()
  match.value = {
    ...match.value,
    status: live.status ?? match.value.status,
    homeScore: live.homeScore ?? match.value.homeScore,
    awayScore: live.awayScore ?? match.value.awayScore,
    gameTimeSeconds: live.gameTimeSeconds ?? match.value.gameTimeSeconds,
    period: live.period ?? match.value.period,
    periodCount: live.periodCount ?? match.value.periodCount,
    periodLengthSeconds: live.periodLengthSeconds ?? match.value.periodLengthSeconds,
    clockRunningSince: live.clockRunningSince ?? null,
    sportCode: live.sportCode ?? match.value.sportCode,
  }
}

async function refreshMatch() {
  const started = Date.now()
  try {
    const { data } = await api.get(`/matches/${route.params.id}`)
    if (started < clockStamp) return
    match.value = data
  } catch {
    /* the socket or the next poll will catch up */
  }
}

function connectClock() {
  client = new Client({
    webSocketFactory: () => new SockJS('/ws') as any,
    connectHeaders: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
    reconnectDelay: 4000,
    onConnect: () => {
      client?.subscribe(`/topic/matches/${route.params.id}`, (message: IMessage) => {
        try {
          applyLiveClock(JSON.parse(message.body))
        } catch {
          /* ignore a bad frame */
        }
      })
    },
  })
  client.activate()
}

async function bump(side: 'home' | 'away', stat: StatId, undo = false) {
  const id = teamId(side)
  if (!id) return
  error.value = ''
  pending.value = true
  try {
    const path = undo ? 'stats/undo' : 'stats'
    const { data } = await api.post(`/analyst/matches/${route.params.id}/${path}`, { teamId: id, stat })
    stats.value = data
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось записать')
  } finally {
    pending.value = false
  }
}

async function hold(side: Side) {
  error.value = ''
  pending.value = true
  try {
    const { data } = await api.post(`/analyst/matches/${route.params.id}/possession`, { side })
    stats.value = data
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось переключить владение')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  reload().catch((e) => {
    error.value = apiError(e, 'Матч не открылся')
  })
  connectClock()
  matchTimer = window.setInterval(refreshMatch, 4000)
  timer = window.setInterval(() => {
    if (possessionSide.value === 'HOME' || possessionSide.value === 'AWAY') {
      api.get(`/matches/${route.params.id}/analyst-stats`).then(({ data }) => {
        stats.value = data
      }).catch(() => {})
    }
  }, 1000)
})

onUnmounted(() => {
  window.clearInterval(timer)
  window.clearInterval(matchTimer)
  client?.deactivate()
})
</script>

<template>
  <section v-if="match" class="stack pad">
    <div class="page-title">
      <RouterLink class="back" to="/analyst">← К матчам</RouterLink>
      <h1>Пульт аналитика</h1>
    </div>

    <div class="panel scoreboard">
      <div class="clock">{{ clockText }}</div>
      <p v-if="clockCaption" class="clock-caption">{{ clockCaption }}</p>
      <StatusBadge :status="match.status" />
      <div class="sides">
        <strong class="club">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="28" />
          {{ homeLabel }}
        </strong>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <strong class="club away">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="28" />
          {{ awayLabel }}
        </strong>
      </div>
    </div>

    <div class="panel stack possession">
      <div class="poss-actions">
        <button
          class="btn large"
          :class="possessionSide === 'HOME' ? 'on' : 'secondary'"
          type="button"
          :disabled="pending"
          :aria-pressed="possessionSide === 'HOME'"
          @click="hold('HOME')"
        >Мяч у хозяев</button>
        <button
          class="btn large"
          :class="possessionSide === 'AWAY' ? 'on' : 'secondary'"
          type="button"
          :disabled="pending"
          :aria-pressed="possessionSide === 'AWAY'"
          @click="hold('AWAY')"
        >Мяч у гостей</button>
      </div>
      <button
        class="btn large pause"
        :class="possessionSide === 'PAUSED' ? 'on' : 'secondary'"
        type="button"
        :disabled="pending"
        :aria-pressed="possessionSide === 'PAUSED'"
        @click="hold('PAUSED')"
      >Пауза владения</button>
      <p v-if="tracked" class="percent">
        <b>{{ stats.homePossessionPercent ?? 0 }}%</b>
        <span>владение</span>
        <b>{{ stats.awayPossessionPercent ?? 0 }}%</b>
      </p>
      <p v-else class="muted">Владение пока не ведётся — на странице матча полоски не будет.</p>
    </div>

    <div class="columns">
      <div class="panel team">
        <div class="head">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="36" />
          <strong>{{ homeLabel }}</strong>
        </div>
        <p class="summary">{{ stats?.home?.shots ?? 0 }} / {{ stats?.home?.shotsOnTarget ?? 0 }} в створ</p>
        <div v-for="stat in STATS" :key="stat.id" class="cell">
          <button class="btn large hit" type="button" :disabled="pending" @click="bump('home', stat.id)">
            <b>{{ countOf('home', stat.id) }}</b>
            <span>{{ stat.label }}</span>
          </button>
          <button
            class="undo"
            type="button"
            :disabled="pending || countOf('home', stat.id) === 0"
            :aria-label="`Убрать: ${stat.label}`"
            @click="bump('home', stat.id, true)"
          >−</button>
        </div>
      </div>
      <div class="panel team">
        <div class="head">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="36" />
          <strong>{{ awayLabel }}</strong>
        </div>
        <p class="summary">{{ stats?.away?.shots ?? 0 }} / {{ stats?.away?.shotsOnTarget ?? 0 }} в створ</p>
        <div v-for="stat in STATS" :key="stat.id" class="cell">
          <button class="btn large hit" type="button" :disabled="pending" @click="bump('away', stat.id)">
            <b>{{ countOf('away', stat.id) }}</b>
            <span>{{ stat.label }}</span>
          </button>
          <button
            class="undo"
            type="button"
            :disabled="pending || countOf('away', stat.id) === 0"
            :aria-label="`Убрать: ${stat.label}`"
            @click="bump('away', stat.id, true)"
          >−</button>
        </div>
      </div>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
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
.clock {
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 9vw, 4rem);
  font-variant-numeric: tabular-nums;
  color: var(--navy);
  line-height: 1;
}
.clock-caption {
  margin: 0;
  color: var(--navy);
  font-weight: 800;
  font-size: 0.95rem;
}
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.75rem;
  align-items: center;
}
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-family: var(--font-display);
}
.club.away { justify-content: flex-end; text-align: right; }
.score { font-size: clamp(1.6rem, 5vw, 2.2rem); font-weight: 800; color: var(--navy); }
.poss-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.btn.on { background: var(--navy); color: #fff; border-color: transparent; }
.btn.pause { width: 100%; }
.percent {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  margin: 0;
  font-variant-numeric: tabular-nums;
  color: var(--navy);
  font-weight: 800;
}
.percent span { color: var(--muted); font-weight: 700; text-align: center; }
.percent b:last-child { text-align: right; }
.columns { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
.team { display: grid; gap: 0.45rem; align-content: start; }
.head { display: flex; align-items: center; gap: 0.45rem; }
.head strong { font-family: var(--font-display); }
.summary { margin: 0; color: var(--muted); font-size: 0.82rem; font-variant-numeric: tabular-nums; }
.cell { position: relative; }
.hit {
  width: 100%;
  min-height: 58px;
  display: grid;
  justify-items: center;
  gap: 0.05rem;
  padding-right: 2rem;
}
.hit b { font-size: 1.15rem; font-variant-numeric: tabular-nums; line-height: 1; }
.hit span { font-size: 0.92rem; line-height: 1.15; }
.undo {
  position: absolute;
  top: 0.4rem;
  right: 0.35rem;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 1px solid var(--line-strong);
  background: #fff;
  color: var(--navy);
  font-weight: 800;
  line-height: 1;
  cursor: pointer;
}
.undo:disabled { opacity: 0.35; cursor: default; }
@media (max-width: 720px) {
  .columns { gap: 0.45rem; }
  .hit span { font-size: 0.78rem; }
  .head strong { font-size: 0.85rem; }
}
</style>
