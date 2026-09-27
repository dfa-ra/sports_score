<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  rows: any[]
  compact?: boolean
}>()

const teams = useTeamDirectory()

const ranked = computed(() =>
  [...props.rows].sort((a, b) =>
    (Number(b.points) - Number(a.points))
    || ((Number(b.goalsFor) - Number(b.goalsAgainst)) - (Number(a.goalsFor) - Number(a.goalsAgainst)))
    || (Number(b.goalsFor) - Number(a.goalsFor))
    || String(a.teamName || '').localeCompare(String(b.teamName || ''), 'ru')
  )
)

function rankClass(index: number) {
  if (index < 2) return 'ice'
  if (index < 4) return 'navy'
  return ''
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
      v-for="(row, i) in ranked"
      :key="row.teamId"
      class="line"
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
  width: 1.35rem;
  height: 1.35rem;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  font-size: 0.7rem;
  font-weight: 800;
  color: var(--muted);
}
.rank.ice { background: var(--ice); color: var(--navy); }
.rank.navy { background: var(--navy); color: #fff; }
@media (max-width: 640px) {
  .line,
  .compact .line {
    grid-template-columns: 2.2rem minmax(0, 1fr) 1.8rem 3.4rem 2.1rem;
    padding: 0 0.55rem;
  }
  .wide { display: none; }
}
</style>
