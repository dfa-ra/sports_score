<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { advancingRank } from '../lib/matchPresentation'
import { stageFullName } from '../lib/qualifyStages'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  rows: any[]
  compact?: boolean
  markedPlaces?: number[]
  /** Place number → stage id. When passed, it replaces the single advancing colour. */
  placeStages?: Record<number, string> | null
}>()

const teams = useTeamDirectory()

function stageId(index: number) {
  if (!props.placeStages) return ''
  return props.placeStages[index + 1] || ''
}

function rankClass(index: number) {
  const stage = stageId(index)
  if (stage) return 'q'
  if (props.placeStages) return ''
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

const desktopTip = ref(false)
let desktopQuery: MediaQueryList | null = null
function syncTip() {
  desktopTip.value = !!desktopQuery?.matches
}
onMounted(() => {
  desktopQuery = window.matchMedia('(min-width: 721px)')
  syncTip()
  desktopQuery.addEventListener('change', syncTip)
})
onUnmounted(() => desktopQuery?.removeEventListener('change', syncTip))

function circleTip(index: number) {
  if (!desktopTip.value) return undefined
  return stageFullName(stageId(index)) || undefined
}

function stageName(index: number) {
  return stageFullName(stageId(index)) || undefined
}

function dismissTip(event: KeyboardEvent) {
  ;(event.currentTarget as HTMLElement).classList.add('tip-off')
}

function clearTip(event: FocusEvent) {
  ;(event.currentTarget as HTMLElement).classList.remove('tip-off')
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
      :class="{ advances: advances(i), q: !!stageId(i) }"
      :data-qualify="stageId(i) || null"
      :to="`/teams/${row.teamId}`"
    >
      <span>
        <span
          class="rank"
          :class="rankClass(i)"
          :tabindex="stageId(i) ? 0 : undefined"
          :title="circleTip(i)"
          :aria-label="stageName(i)"
          @keydown.escape.prevent="dismissTip"
          @blur="clearTip"
        >{{ i + 1 }}<span v-if="stageName(i)" class="stage-tip" aria-hidden="true">{{ stageName(i) }}</span></span>
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
.line:not(.head) { color: #00205b; }
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
  position: relative;
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
.line.q {
  background: var(--q-row);
  box-shadow: inset var(--q-bar-width, 0px) 0 0 var(--q-bar, transparent);
}
.line.q:hover { background: var(--q-row); }
.rank.q {
  background: var(--q-circle);
  color: var(--q-digit, #00205b);
  border: var(--q-stroke-width, 0px) solid var(--q-stroke, transparent);
}
@media (max-width: 720px) {
  .line,
  .compact .line {
    grid-template-columns: 22px minmax(0, 1fr) 24px 48px 28px;
    min-width: 0;
    width: 100%;
    min-height: 44px;
    height: 44px;
    box-sizing: border-box;
    padding: 0 14px 0 12px;
    border-left: 4px solid transparent;
    font-size: 14px;
  }
  .line > span:not(.club):not(.team-label) { text-align: right; }
  .line > span:first-child { text-align: center; }
  .line.q {
    border-left-color: var(--q-bar);
    box-shadow: none;
  }
  .head {
    height: 34px;
    min-height: 34px;
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.02em;
  }
  .wide { display: none; }
  .club,
  .team-label { margin-left: 8px; }
  .club { grid-template-columns: 22px minmax(0, 1fr); gap: 8px; min-width: 0; }
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
    box-sizing: border-box;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: transparent;
    color: #8b97a8;
    font-size: 12px;
    font-weight: 700;
  }
  .rank.ice, .rank.navy { background: transparent; color: #8b97a8; }
  .rank.q {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: var(--q-circle);
    color: var(--q-digit, #00205b);
    border: var(--q-stroke-width, 0px) solid var(--q-stroke, transparent);
  }
  .points { font-size: 14px; font-weight: 700; }
}
.rank:focus { outline: none; }
.rank:focus-visible {
  outline: 2px solid #4cb4e5;
  outline-offset: 2px;
}
.stage-tip {
  position: absolute;
  z-index: 2;
  left: 50%;
  bottom: calc(100% + 8px);
  transform: translateX(-50%);
  display: none;
  padding: 0.2rem 0.45rem;
  border-radius: 6px;
  background: #00205b;
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  font-style: normal;
  line-height: 1.2;
  letter-spacing: 0;
  white-space: nowrap;
  pointer-events: none;
}
.rank:focus-visible:not(.tip-off) .stage-tip { display: block; }
</style>
