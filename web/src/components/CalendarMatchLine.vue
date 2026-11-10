<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { statusLabel } from '../lib/format'
import { cardScoreParts, cardScoreText, rowClock, sideName } from '../lib/calendarFeed'
import { matchOutcome } from '../lib/match'
import { useFavorites } from '../stores/favorites'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  match: any
  homeName: string
  awayName: string
  homeLogo?: string | null
  awayLogo?: string | null
  kicker?: string
}>()

const fav = useFavorites()
const clock = computed(() => rowClock(props.match))
const score = computed(() => cardScoreText(props.match))
const parts = computed(() => cardScoreParts(props.match))
const badge = computed(() => statusLabel[props.match?.status] || props.match?.status || '')
const tone = computed(() => {
  const status = props.match?.status
  if (status === 'LIVE') return 'live'
  if (status === 'PAUSED') return 'paused'
  if (status === 'CANCELLED') return 'cancelled'
  if (status === 'FINISHED') return 'finished'
  return 'soon'
})

function weight(side: 'home' | 'away') {
  const id = side === 'home' ? props.match?.homeTeamId : props.match?.awayTeamId
  const outcome = matchOutcome(props.match, id)
  if (outcome === 'WIN') return 'win'
  if (outcome === 'LOSS') return 'lose'
  if (props.match?.status === 'CANCELLED') return 'lose'
  return ''
}

const homeLabel = computed(() => sideName(props.homeName, props.match?.homeTeamName))
const awayLabel = computed(() => sideName(props.awayName, props.match?.awayTeamName))
</script>

<template>
  <div class="line" :class="tone">
    <button
      class="star"
      type="button"
      :class="{ on: fav.hasMatch(match.id) }"
      :aria-label="fav.hasMatch(match.id) ? 'Убрать из избранного' : 'В избранное'"
      @click="fav.toggleMatch(match.id)"
    >★</button>
    <RouterLink class="body" :to="`/matches/${match.id}`">
      <span class="when">
        <em v-if="kicker" class="kicker">{{ kicker }}</em>
        <b>{{ clock }}</b>
        <span v-if="badge" class="phone-dot" aria-hidden="true">{{ '\u00A0·\u00A0' }}</span>
        <i class="badge phone" :class="tone">{{ badge }}</i>
      </span>
      <span class="pair">
        <span class="side home" :class="weight('home')">
          <TeamCrest :src="homeLogo" :name="homeLabel" :size="28" />
          <span>{{ homeLabel }}</span>
        </span>
        <span class="score desk">{{ score }}</span>
        <span class="side away" :class="weight('away')">
          <TeamCrest :src="awayLogo" :name="awayLabel" :size="28" />
          <span>{{ awayLabel }}</span>
        </span>
      </span>
      <span class="score phone">
        <b>{{ parts.home }}</b>
        <b>{{ parts.away }}</b>
      </span>
      <i class="badge desk" :class="tone">{{ badge }}</i>
    </RouterLink>
  </div>
</template>

<style scoped>
.line {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: stretch;
  background: #fff;
  border-top: 1px solid var(--line);
  min-width: 0;
}
.line.live { box-shadow: inset 3px 0 0 var(--status-live); background: #fff7f5; }
.line.paused { box-shadow: inset 3px 0 0 var(--status-warn); background: #fffaf1; }
.line.cancelled { color: var(--muted); }
.star {
  border: 0;
  background: transparent;
  color: #c5ced8;
  cursor: pointer;
  font-size: 1rem;
}
.star.on { color: var(--ice); }
.body {
  display: grid;
  grid-template-columns: 4.6rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.7rem 0.85rem 0.7rem 0;
  color: inherit;
  text-decoration: none;
  min-width: 0;
}
.body:hover { color: inherit; }
.when {
  display: grid;
  justify-items: start;
  gap: 0.2rem;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.when b { color: var(--navy); font-weight: 800; font-size: 0.92rem; }
.kicker {
  font-style: normal;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.03em;
  color: var(--navy);
  background: #e8eef8;
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
  max-width: 9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
  min-width: 0;
}
.side {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  color: var(--ink);
  font-weight: 650;
}
.side.home { justify-content: flex-end; text-align: right; }
.side span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side.win { font-weight: 800; }
.side.lose { color: var(--muted); font-weight: 600; }
.score {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  color: var(--navy);
  white-space: nowrap;
}
.score.phone { display: none; }
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 0.12rem 0.55rem;
  font-style: normal;
  font-size: 0.72rem;
  font-weight: 800;
  background: var(--status-soon-soft);
  color: var(--status-soon-ink);
}
.badge.live { background: var(--status-live-soft); color: var(--status-live-ink); }
.badge.paused { background: var(--status-warn-soft); color: var(--status-warn-ink); }
.badge.finished { background: var(--status-done-soft); color: var(--status-done-ink); }
.badge.cancelled { background: #eef2f6; color: #8b97a6; }
.badge.phone { display: none; }
.phone-dot { display: none; }
@media (max-width: 720px) {
  .line {
    grid-template-columns: 28px minmax(0, 1fr);
    column-gap: 8px;
    min-height: var(--row-match);
  }
  .star {
    width: 28px;
    min-width: 28px;
    padding: 0;
    font-size: 14px;
    line-height: 14px;
    color: #9aa7b8;
    position: relative;
    display: grid;
    place-items: center;
  }
  .star.on { color: #4cb4e5; }
  .star::before {
    content: "";
    position: absolute;
    left: 50%;
    top: 50%;
    width: 44px;
    height: 44px;
    transform: translate(-50%, -50%);
  }
  .body {
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: 8px;
    min-height: var(--row-match);
    padding: 4px 8px 4px 0;
    align-items: center;
  }
  .when {
    display: flex;
    flex-flow: row wrap;
    align-items: baseline;
    column-gap: 0;
  }
  .kicker { flex: 1 0 100%; }
  .when b { font-size: 12px; font-weight: 800; line-height: 16px; }
  .phone-dot { display: inline; white-space: pre; font-size: 12px; line-height: 16px; color: #5b6b82; }
  .badge.phone { font-size: 10.5px; font-weight: 500; line-height: 14px; }
  .pair { grid-template-columns: minmax(0, 1fr); gap: 2px; }
  .side.home, .side.away { justify-content: flex-start; text-align: left; }
  .side { font-size: 14px; line-height: 18px; }
  .side :deep(.team-crest) { width: 20px !important; height: 20px !important; font-size: 10px !important; }
  .score.desk, .badge.desk { display: none; }
  .score.phone, .badge.phone { display: grid; }
  .score.phone { justify-items: end; gap: 2px; font-size: 15px; font-weight: 800; }
}
</style>
