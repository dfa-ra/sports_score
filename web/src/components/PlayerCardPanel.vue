<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ageLine, formatMatchDay, outcomeMark } from '../lib/format'
import PlayerAvatar from './PlayerAvatar.vue'
import TeamCrest from './TeamCrest.vue'

type MatchRow = {
  matchId: string
  scheduledAt?: string
  tournamentName?: string | null
  homeTeamName?: string
  awayTeamName?: string
  homeTeamLogoUrl?: string | null
  awayTeamLogoUrl?: string | null
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
  { key: 'assists', label: 'Пас' },
  { key: 'yellowCards', label: 'Жёлтые' },
  { key: 'redCards', label: 'Красные' },
  { key: 'cleanSheets', label: 'Сухие' },
]

const props = defineProps<{
  card: any
  editable?: boolean
}>()

defineEmits<{
  edit: []
}>()

const tab = ref<'games' | 'stats'>('games')
const name = computed(() => props.card.displayName || `${props.card.firstName || ''} ${props.card.lastName || ''}`.trim())
const age = computed(() => ageLine(props.card.dateOfBirth))
const history = computed<MatchRow[]>(() => props.card.matchHistory ?? [])
const stats = computed(() => {
  const raw = props.card.statistics
  if (!raw || typeof raw !== 'object') return []
  return STATS
    .filter((item) => raw[item.key] !== undefined && raw[item.key] !== null)
    .map((item) => ({ ...item, value: raw[item.key] }))
})

function marks(row: MatchRow) {
  return [
    row.minutesPlayed != null ? `${row.minutesPlayed}'` : null,
    row.goals ? `${row.goals}Г` : null,
    row.assists ? `${row.assists}П` : null,
    row.yellowCards ? `${row.yellowCards}Ж` : null,
    row.redCards ? `${row.redCards}К` : null,
  ].filter(Boolean).join(' · ')
}
</script>

<template>
  <div class="wrap">
    <div class="identity">
      <PlayerAvatar :src="card.avatarUrl" :name="name" :size="76" tile />
      <div class="who">
        <h1>{{ name }}</h1>
        <p v-if="age" class="meta">{{ age }}</p>
        <p v-if="card.jerseyNumber != null || !card.team" class="meta">
          <span v-if="card.jerseyNumber != null">№{{ card.jerseyNumber }}</span>
          <template v-if="!card.team">
            <span v-if="card.jerseyNumber != null"> · </span>
            {{ card.position || 'Игрок' }}
          </template>
        </p>
      </div>
      <button v-if="editable" class="pen" type="button" aria-label="Изменить" @click="$emit('edit')">✎</button>
    </div>

    <RouterLink v-if="card.team" class="club-bar" :to="`/teams/${card.team.id}`">
      <TeamCrest :src="card.team.logoUrl" :name="card.team.name" :size="22" />
      <b>{{ card.team.name }}</b>
      <span>{{ card.position || 'Игрок' }}</span>
    </RouterLink>

    <div class="fs-tabs">
      <button type="button" :class="{ on: tab === 'games' }" @click="tab = 'games'">Игры</button>
      <button type="button" :class="{ on: tab === 'stats' }" @click="tab = 'stats'">Цифры</button>
    </div>

    <div v-if="tab === 'stats'" class="stats">
      <div v-for="item in stats" :key="item.key" class="stat">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
      <p v-if="!stats.length" class="muted empty">Пока без цифр.</p>
    </div>

    <div v-else class="games">
      <p v-if="!history.length" class="muted empty">Матчей пока нет.</p>
      <RouterLink v-for="m in history" :key="m.matchId" class="game" :to="`/matches/${m.matchId}`">
        <span class="when">{{ formatMatchDay(m.scheduledAt).slice(0, 5) }}</span>
        <span class="sides">
          <span :class="{ own: m.home }">
            <TeamCrest :src="m.homeTeamLogoUrl" :name="m.homeTeamName" :size="16" />
            {{ m.homeTeamName }}
          </span>
          <span :class="{ own: !m.home }">
            <TeamCrest :src="m.awayTeamLogoUrl" :name="m.awayTeamName" :size="16" />
            {{ m.awayTeamName }}
          </span>
        </span>
        <span class="nums">
          <b>{{ m.homeScore }}</b>
          <b>{{ m.awayScore }}</b>
        </span>
        <span v-if="m.outcome" class="result" :class="m.outcome.toLowerCase()">{{ outcomeMark[m.outcome] || m.outcome }}</span>
        <span v-if="marks(m)" class="marks">{{ marks(m) }}</span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: grid; gap: 0; }
.identity {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.15rem 0 0.75rem;
}
.who { min-width: 0; }
.who h1 {
  margin: 0;
  font-size: clamp(1.25rem, 5vw, 1.7rem);
  line-height: 1.1;
}
.meta { margin: 0.15rem 0 0; color: var(--muted); font-size: 0.86rem; }
.pen {
  width: 40px;
  height: 40px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  color: var(--navy);
  font-size: 1.1rem;
  cursor: pointer;
}
.club-bar {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.55rem;
  align-items: center;
  padding: 0.65rem 0.75rem;
  background: #f4f7fb;
  border-radius: 10px;
  color: inherit;
  margin-bottom: 0.65rem;
}
.club-bar b { font-weight: 800; color: var(--navy); }
.club-bar span { color: var(--muted); font-size: 0.86rem; }
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
  padding: 0.65rem 0;
}
.stat {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.6rem 0.7rem;
  background: #fff;
}
.stat span {
  display: block;
  color: var(--muted);
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.stat strong { font-size: 1.25rem; color: var(--navy); }
.games { background: #fff; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
.game {
  display: grid;
  grid-template-columns: 36px 1fr auto auto;
  grid-template-areas:
    "when sides nums mark"
    "when marks nums mark";
  gap: 0.15rem 0.55rem;
  align-items: center;
  padding: 0.65rem 0.7rem;
  border-bottom: 1px solid var(--line);
  color: inherit;
  text-decoration: none;
}
.game:last-child { border-bottom: 0; }
.when { grid-area: when; color: var(--muted); font-size: 0.72rem; font-variant-numeric: tabular-nums; }
.sides { grid-area: sides; display: grid; gap: 0.18rem; min-width: 0; }
.sides span {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
}
.sides .own { font-weight: 800; color: var(--navy); }
.nums {
  grid-area: nums;
  display: grid;
  justify-items: end;
  font-weight: 800;
  color: var(--navy);
  font-variant-numeric: tabular-nums;
}
.marks { grid-area: marks; color: var(--muted); font-size: 0.72rem; }
.result {
  grid-area: mark;
  width: 1.35rem;
  height: 1.35rem;
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 800;
}
.result.win { background: #1b8a4a; }
.result.draw { background: #c47b00; }
.result.loss { background: var(--danger); }
.empty { padding: 0.85rem; margin: 0; }
</style>
