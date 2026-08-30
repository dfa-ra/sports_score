<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { initials, outcomeMark } from '../lib/format'
import { matchOutcome, shortKickoff } from '../lib/match'
import { useFavorites } from '../stores/favorites'

const props = defineProps<{
  match: any
  homeName: string
  awayName: string
  highlightTeamId?: string | null
}>()

const fav = useFavorites()
const outcome = computed(() => matchOutcome(props.match, props.highlightTeamId))
const when = computed(() => shortKickoff(props.match.scheduledAt, props.match.status))
</script>

<template>
  <div class="row">
    <button
      class="star"
      type="button"
      :class="{ on: fav.hasMatch(match.id) }"
      :aria-label="fav.hasMatch(match.id) ? 'Убрать из избранного' : 'В избранное'"
      @click.stop="fav.toggleMatch(match.id)"
    >★</button>
    <RouterLink class="body" :to="`/matches/${match.id}`">
      <span class="when" :class="{ live: match.status === 'LIVE' || match.status === 'PAUSED' }">{{ when }}</span>
      <span class="sides">
        <span class="side" :class="{ own: highlightTeamId === match.homeTeamId }">
          <i>{{ initials(homeName) }}</i>
          <b>{{ homeName }}</b>
        </span>
        <span class="side" :class="{ own: highlightTeamId === match.awayTeamId }">
          <i>{{ initials(awayName) }}</i>
          <b>{{ awayName }}</b>
        </span>
      </span>
      <span class="nums">
        <strong>{{ match.homeScore }}</strong>
        <strong>{{ match.awayScore }}</strong>
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
.body {
  display: grid;
  grid-template-columns: 52px 1fr auto auto;
  gap: 0.55rem;
  align-items: center;
  padding: 0.55rem 0.7rem 0.55rem 0;
  color: inherit;
  text-decoration: none;
  min-height: 56px;
}
.body:hover { color: inherit; background: rgba(76, 180, 229, 0.06); }
.when {
  font-size: 0.72rem;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.when.live { color: var(--ice); font-weight: 800; }
.sides { display: grid; gap: 0.18rem; min-width: 0; }
.side {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 0.4rem;
  align-items: center;
  min-width: 0;
}
.side i {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--navy);
  font-size: 0.48rem;
  font-style: normal;
  font-weight: 800;
}
.side b {
  font-weight: 500;
  font-size: 0.92rem;
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
