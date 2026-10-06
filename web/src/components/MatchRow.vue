<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { outcomeMark } from '../lib/format'
import { matchOutcome } from '../lib/match'
import KickoffWhen from './KickoffWhen.vue'
import ScorePop from './ScorePop.vue'
import { useFavorites } from '../stores/favorites'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  match: any
  homeName: string
  awayName: string
  highlightTeamId?: string | null
}>()

const fav = useFavorites()
const teams = useTeamDirectory()
const outcome = computed(() => matchOutcome(props.match, props.highlightTeamId))
const isLive = computed(() => props.match.status === 'LIVE' || props.match.status === 'PAUSED')
const homeLogo = computed(() => teams.logo(props.match.homeTeamId))
const awayLogo = computed(() => teams.logo(props.match.awayTeamId))
const venue = computed(() => {
  const raw = props.match?.venue
  return typeof raw === 'string' ? raw.trim() : ''
})
const starTap = ref(0)

function tapStar() {
  fav.toggleMatch(props.match.id)
  starTap.value += 1
}
</script>

<template>
  <div class="row">
    <button
      class="star"
      type="button"
      :class="{ on: fav.hasMatch(match.id) }"
      :aria-label="fav.hasMatch(match.id) ? 'Убрать из избранного' : 'В избранное'"
      @click.stop="tapStar"
    ><span class="star-glyph" :key="starTap" :class="{ 'star-tap': starTap }">★</span></button>
    <RouterLink class="body" :to="`/matches/${match.id}`">
      <span class="kick-slot">
        <KickoffWhen :at="match.scheduledAt" :status="match.status" :minute="match.minute" />
        <i v-if="match.status === 'LIVE'" class="live-dot" aria-hidden="true" />
      </span>
      <span class="sides">
        <span class="side" :class="{ own: highlightTeamId === match.homeTeamId }">
          <TeamCrest :src="homeLogo" :name="homeName" :size="28" />
          <b>{{ homeName }}</b>
        </span>
        <span class="side" :class="{ own: highlightTeamId === match.awayTeamId }">
          <TeamCrest :src="awayLogo" :name="awayName" :size="28" />
          <b>{{ awayName }}</b>
        </span>
        <span v-if="venue" class="venue">{{ venue }}</span>
        <span v-if="isLive && match.lastGoalScorer" class="scorer">{{ match.lastGoalScorer }}</span>
      </span>
      <span class="nums">
        <strong><ScorePop :value="match.homeScore" /></strong>
        <strong><ScorePop :value="match.awayScore" /></strong>
      </span>
      <span v-if="outcome" class="mark" :class="outcome.toLowerCase()">{{ outcomeMark[outcome] }}</span>
    </RouterLink>
  </div>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 28px 1fr;
  align-items: stretch;
  border-bottom: 1px solid var(--line);
  background: #fff;
}
.star {
  border: 0;
  background: transparent;
  color: #c5ced8;
  font-size: 1rem;
  cursor: pointer;
  padding: 0;
}
.star.on { color: var(--ice); }
.kick-slot {
  position: relative;
  display: flex;
  justify-content: center;
}
.kick-slot .live-dot {
  position: absolute;
  left: 0;
  bottom: 0.15rem;
}
.body {
  display: grid;
  grid-template-columns: 46px 1fr auto auto;
  gap: 0.55rem;
  align-items: center;
  padding: 0.55rem 0.7rem 0.55rem 0;
  color: inherit;
  text-decoration: none;
  min-height: 56px;
}
.body:hover { color: inherit; background: rgba(76, 180, 229, 0.06); }
.scorer {
  font-size: 0.68rem;
  color: var(--muted);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sides { display: grid; gap: 0.18rem; min-width: 0; }
.venue {
  font-size: 0.72rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 0.4rem;
  align-items: center;
  min-width: 0;
}
.side :deep(.team-crest) {
  width: 28px;
  height: 28px;
  min-width: 28px;
  min-height: 28px;
  object-fit: contain;
  object-position: center;
}
.side b {
  font-weight: 500;
  font-size: 1rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side.own b { font-weight: 800; color: var(--navy); }
.nums {
  display: grid;
  justify-items: end;
  gap: 0.18rem;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  color: var(--navy);
  min-width: 1.1rem;
}
.mark {
  width: 1.35rem;
  height: 1.35rem;
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 800;
}
.mark.win { background: #1b8a4a; }
.mark.draw { background: #c47b00; }
.mark.loss { background: var(--danger); }
</style>
