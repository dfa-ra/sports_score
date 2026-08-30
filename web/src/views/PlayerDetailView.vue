<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../api/client'
import { formatMatchDay, outcomeMark } from '../lib/format'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { useAuthStore } from '../stores/auth'
import AdminOnly from '../components/AdminOnly.vue'
import CopyChip from '../components/CopyChip.vue'

type MatchRow = {
  matchId: string
  scheduledAt?: string
  tournamentName?: string | null
  homeTeamName?: string
  awayTeamName?: string
  opponentName?: string
  home?: boolean
  homeScore?: number
  awayScore?: number
  status?: string
  outcome?: string | null
  goals?: number
  assists?: number
  yellowCards?: number
  redCards?: number
  minutesPlayed?: number | null
}

const STATS: { key: string; label: string }[] = [
  { key: 'appearances', label: 'Игры' },
  { key: 'goals', label: 'Голы' },
  { key: 'assists', label: 'Передачи' },
  { key: 'yellowCards', label: 'Жёлтые' },
  { key: 'redCards', label: 'Красные' },
  { key: 'cleanSheets', label: 'Сухие' },
]

const auth = useAuthStore()
const route = useRoute()
const card = ref<any>(null)

const stats = computed(() => {
  const raw = card.value?.statistics
  if (!raw || typeof raw !== 'object') return []
  return STATS
    .filter((item) => raw[item.key] !== undefined && raw[item.key] !== null)
    .map((item) => ({ ...item, value: raw[item.key] }))
})

const history = computed<MatchRow[]>(() => card.value?.matchHistory ?? [])

onMounted(async () => {
  const { data } = await api.get(`/players/${route.params.id}/card`)
  card.value = data
})
</script>

<template>
  <section v-if="card" class="stack page">
    <div class="head">
      <PlayerAvatar
        :src="card.avatarUrl"
        :name="card.displayName || `${card.firstName} ${card.lastName}`"
        :size="88"
      />
      <div class="who">
        <p v-if="card.team" class="eyebrow">
          <RouterLink :to="`/teams/${card.team.id}`">{{ card.team.shortName || card.team.name }}</RouterLink>
        </p>
        <h1>{{ card.displayName || `${card.firstName} ${card.lastName}` }}</h1>
        <p class="meta">
          {{ card.position || 'Игрок' }}
          <span v-if="card.jerseyNumber != null">· №{{ card.jerseyNumber }}</span>
          <span v-if="card.team">· {{ card.team.name }}</span>
        </p>
      </div>
    </div>

    <div class="panel">
      <h2>Цифры</h2>
      <div class="stats">
        <div v-for="item in stats" :key="item.key" class="stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </div>

    <div class="panel history">
      <h2>История матчей</h2>
      <p v-if="!history.length" class="muted none">Матчей пока нет.</p>
      <div v-else class="table-wrap">
        <table class="table matches">
          <thead>
            <tr>
              <th>Дата</th>
              <th>Матч</th>
              <th class="num">Мин</th>
              <th class="num">Г</th>
              <th class="num">П</th>
              <th class="num">Ж</th>
              <th class="num">К</th>
              <th class="num">Итог</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in history" :key="m.matchId">
              <td class="date">
                <RouterLink :to="`/matches/${m.matchId}`">{{ formatMatchDay(m.scheduledAt) }}</RouterLink>
                <small v-if="m.tournamentName">{{ m.tournamentName }}</small>
              </td>
              <td>
                <RouterLink class="fixture" :to="`/matches/${m.matchId}`">
                  <span :class="{ own: m.home }">{{ m.homeTeamName }}</span>
                  <strong class="scoreline">{{ m.homeScore }}:{{ m.awayScore }}</strong>
                  <span :class="{ own: !m.home }">{{ m.awayTeamName }}</span>
                </RouterLink>
              </td>
              <td class="num">{{ m.minutesPlayed ?? '—' }}</td>
              <td class="num" :class="{ hit: m.goals }">{{ m.goals || 0 }}</td>
              <td class="num" :class="{ hit: m.assists }">{{ m.assists || 0 }}</td>
              <td class="num" :class="{ 'card-y': m.yellowCards }">{{ m.yellowCards || 0 }}</td>
              <td class="num" :class="{ 'card-r': m.redCards }">{{ m.redCards || 0 }}</td>
              <td class="num">
                <span v-if="m.outcome" class="result" :class="m.outcome.toLowerCase()">
                  {{ outcomeMark[m.outcome] || m.outcome }}
                </span>
                <span v-else class="muted">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <AdminOnly v-if="auth.canManageLeague" title="Для админа">
      <CopyChip :value="String(card.id)" label="Скопировать id игрока" />
    </AdminOnly>
  </section>
</template>

<style scoped>
.page { gap: 0.75rem; }
.head {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.15rem;
}
.who { min-width: 0; }
.who h1 {
  margin: 0.1rem 0 0.15rem;
  font-size: clamp(1.55rem, 3vw, 2.1rem);
  line-height: 1.05;
}
.meta { color: var(--muted); margin: 0; }
h2 { font-size: 1.05rem; margin: 0 0 0.65rem; }
.none { margin: 0; }
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
  gap: 0.55rem;
}
.stat {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.65rem 0.75rem;
  background: color-mix(in srgb, var(--ice) 10%, white);
}
.stat span {
  display: block;
  color: var(--muted);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.stat strong {
  font-family: var(--font-display);
  font-size: 1.35rem;
  color: var(--navy);
}
.matches { min-width: 640px; }
.matches th, .matches td { padding: 0.62rem 0.45rem; }
.matches th.num, .matches td.num { text-align: center; width: 2.4rem; }
.date { white-space: nowrap; }
.date small {
  display: block;
  color: var(--muted);
  font-size: 0.7rem;
  letter-spacing: 0.02em;
}
.fixture {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.45rem;
  align-items: center;
  color: inherit;
  text-decoration: none;
}
.fixture span { min-width: 0; }
.fixture span:last-child { text-align: right; }
.fixture .own { color: var(--navy); font-weight: 700; }
.scoreline {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  color: var(--navy);
}
.hit { color: var(--navy); font-weight: 800; }
.card-y { color: #c47b00; font-weight: 800; }
.card-r { color: var(--danger); font-weight: 800; }
.result {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.45rem;
  height: 1.45rem;
  border-radius: 5px;
  font-size: 0.72rem;
  font-weight: 800;
  color: white;
}
.result.win { background: #1b8a4a; }
.result.draw { background: #c47b00; }
.result.loss { background: var(--danger); }
@media (max-width: 719px) {
  .head { gap: 0.7rem; }
  .who h1 { font-size: 1.35rem; }
  .stats { grid-template-columns: repeat(3, 1fr); }
}
</style>
