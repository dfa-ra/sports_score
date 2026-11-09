<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { advancingRank } from '../lib/matchPresentation'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  rows: any[]
  compact?: boolean
  markedPlaces?: number[]
}>()

const teams = useTeamDirectory()

function rankClass(index: number) {
  // A passed list, including an empty one, is the only highlight. The old
  // 1–2 / 3–4 colours stay only for callers that do not pass places.
  if (props.markedPlaces) return advancingRank(index, props.markedPlaces) ? 'ice' : ''
  if (index < 2) return 'ice'
  if (index < 4) return 'navy'
  return ''
}

function advances(index: number) {
  if (!props.markedPlaces) return false
  return advancingRank(index, props.markedPlaces)
}
</script>

<template>
  <div class="board" :class="{ compact }">
    <div class="line head">
      <span>#</span>
      <span class="team-label">Команда</span>
      <span>И</span>
      <span v-if="!compact" class="wide">В</span>
      <span v-if="!compact" class="wide">Н</span>
      <span v-if="!compact" class="wide">П</span>
      <span>Г</span>
      <span>О</span>
    </div>
    <RouterLink
      v-for="(row, i) in rows"
      :key="row.teamId"
      class="line"
      :class="{ advances: advances(i) }"
      :to="`/teams/${row.teamId}`"
    >
      <span>
        <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
      </span>
      <span class="club">
        <TeamCrest :src="teams.logo(row.teamId)" :name="row.teamName" :size="22" />
        <b>{{ row.teamName }}</b>
      </span>
      <span>{{ row.played }}</span>
      <span v-if="!compact" class="wide">{{ row.wins }}</span>
      <span v-if="!compact" class="wide">{{ row.draws }}</span>
      <span v-if="!compact" class="wide">{{ row.losses }}</span>
      <span>{{ row.goalsFor }}:{{ row.goalsAgainst }}</span>
      <span class="points">{{ row.points }}</span>
    </RouterLink>
  </div>
</template>

<style scoped>
.board { width: 100%; }
.line {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) 2.35rem 2.35rem 2.35rem 2.35rem 4rem 2.5rem;
  align-items: center;
  min-height: 2.65rem;
  padding: 0 0.85rem;
  border-bottom: 1px solid var(--line);
  color: inherit;
  text-decoration: none;
}
.compact .line {
  grid-template-columns: 2.5rem minmax(0, 1fr) 2.35rem 4rem 2.5rem;
}
.line > span:not(.club):not(.team-label) {
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.line:not(.head):hover { background: rgba(76, 180, 229, 0.08); }
.line.advances { background: var(--accent-soft); }
.team-label { text-align: left; }
.head {
  min-height: 2.15rem;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.line:last-child { border-bottom: 0; }
.club {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 0.45rem;
  align-items: center;
  min-width: 0;
  text-align: left;
}
.club b {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.points { font-weight: 800; }
.rank {
  width: 1.45rem;
  height: 1.45rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  padding: 0;
  line-height: 1;
  font-size: 0.72rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--muted);
}
.rank.ice { background: var(--ice); color: var(--navy); }
.rank.navy { background: var(--navy); color: #fff; }
@media (max-width: 720px) {
  .line,
  .compact .line {
    grid-template-columns: 22px minmax(0, 1fr) 36px 36px 34px;
    min-width: 0;
    width: 100%;
    min-height: 44px;
    height: 44px;
    padding: 0 2px 0 12px;
    font-size: 14px;
  }
  .head {
    height: 34px;
    min-height: 34px;
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.02em;
  }
  .wide { display: none; }
  .club { grid-template-columns: 22px minmax(0, 1fr); gap: 6px; min-width: 0; }
  .club :deep(.team-crest) { width: 22px !important; height: 22px !important; font-size: 10px !important; }
  .club b {
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    line-height: 18px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rank {
    width: auto;
    height: auto;
    border-radius: 0;
    background: transparent;
    color: #8b97a8;
    font-size: 12px;
    font-weight: 700;
  }
  .rank.ice, .rank.navy { background: transparent; color: #8b97a8; }
  .points { font-size: 14px; font-weight: 700; }
}
</style>
