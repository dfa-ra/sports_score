<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../../api/client'
import { apiError } from '../../lib/errors'
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
const assignment = ref<any>(null)
const error = ref('')
const pending = ref(false)
let timer = 0

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))
const closed = computed(() => match.value?.status === 'FINISHED' || match.value?.status === 'CANCELLED')
const coverage = computed<'BOTH' | 'HOME' | 'AWAY' | null>(() => {
  if (auth.canManageLeague) return 'BOTH'
  const me = auth.user?.id
  const row = assignment.value
  if (!me || !row?.mode) return null
  if (row.mode === 'BOTH' && row.analyst?.id === me) return 'BOTH'
  if (row.mode === 'SPLIT' && row.homeAnalyst?.id === me) return 'HOME'
  if (row.mode === 'SPLIT' && row.awayAnalyst?.id === me) return 'AWAY'
  return null
})
const showHome = computed(() => coverage.value === 'BOTH' || coverage.value === 'HOME')
const showAway = computed(() => coverage.value === 'BOTH' || coverage.value === 'AWAY')
const canWrite = computed(() => !closed.value && coverage.value != null)
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
  const [m, s, a] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/analyst-stats`),
    api.get(`/matches/${id}/analyst-assignment`),
  ])
  match.value = m.data
  stats.value = s.data
  assignment.value = a.data
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
  timer = window.setInterval(() => {
    if (possessionSide.value === 'HOME' || possessionSide.value === 'AWAY') {
      api.get(`/matches/${route.params.id}/analyst-stats`).then(({ data }) => {
        stats.value = data
      }).catch(() => {})
    }
  }, 1000)
})

onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <section v-if="match" class="stack pad">
    <div class="page-title">
      <RouterLink class="back" to="/analyst">← К матчам</RouterLink>
      <h1>Пульт аналитика</h1>
    </div>

    <div class="panel scoreboard">
      <StatusBadge :status="match.status" />
      <p v-if="closed" class="closed">Матч уже завершён</p>
      <p v-else-if="!coverage" class="closed">Вы не назначены на этот матч</p>
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

    <div v-if="coverage" class="panel stack possession">
      <div class="poss-actions" :class="{ single: coverage !== 'BOTH' }">
        <button
          v-if="showHome"
          class="btn large"
          :class="possessionSide === 'HOME' ? 'on' : 'secondary'"
          type="button"
          :disabled="pending || !canWrite"
          :aria-pressed="possessionSide === 'HOME'"
          @click="hold('HOME')"
        >Мяч у хозяев</button>
        <button
          v-if="showAway"
          class="btn large"
          :class="possessionSide === 'AWAY' ? 'on' : 'secondary'"
          type="button"
          :disabled="pending || !canWrite"
          :aria-pressed="possessionSide === 'AWAY'"
          @click="hold('AWAY')"
        >Мяч у гостей</button>
      </div>
      <button
        class="btn large pause"
        :class="possessionSide === 'PAUSED' ? 'on' : 'secondary'"
        type="button"
        :disabled="pending || !canWrite"
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

    <div v-if="coverage" class="columns" :class="{ single: coverage !== 'BOTH' }">
      <div v-if="showHome" class="panel team">
        <div class="head">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="36" />
          <strong>{{ homeLabel }}</strong>
        </div>
        <p class="summary">{{ stats?.home?.shots ?? 0 }} / {{ stats?.home?.shotsOnTarget ?? 0 }} в створ</p>
        <div v-for="stat in STATS" :key="stat.id" class="cell">
          <button class="btn large hit" type="button" :disabled="pending || !canWrite" @click="bump('home', stat.id)">
            <b>{{ countOf('home', stat.id) }}</b>
            <span>{{ stat.label }}</span>
          </button>
          <button
            class="undo"
            type="button"
            :disabled="pending || !canWrite || countOf('home', stat.id) === 0"
            :aria-label="`Убрать: ${stat.label}`"
            @click="bump('home', stat.id, true)"
          >−</button>
        </div>
      </div>
      <div v-if="showAway" class="panel team">
        <div class="head">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="36" />
          <strong>{{ awayLabel }}</strong>
        </div>
        <p class="summary">{{ stats?.away?.shots ?? 0 }} / {{ stats?.away?.shotsOnTarget ?? 0 }} в створ</p>
        <div v-for="stat in STATS" :key="stat.id" class="cell">
          <button class="btn large hit" type="button" :disabled="pending || !canWrite" @click="bump('away', stat.id)">
            <b>{{ countOf('away', stat.id) }}</b>
            <span>{{ stat.label }}</span>
          </button>
          <button
            class="undo"
            type="button"
            :disabled="pending || !canWrite || countOf('away', stat.id) === 0"
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
.closed { margin: 0; font-weight: 800; color: var(--navy); }
.poss-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.poss-actions.single { grid-template-columns: 1fr; }
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
.columns.single { grid-template-columns: 1fr; }
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
