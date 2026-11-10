<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { advancingRank } from '../lib/matchPresentation'
import { stageFullName } from '../lib/qualifyStages'
import type { StandingGroup } from '../lib/standings'
import { useTeamDirectory } from '../lib/useTeamDirectory'
import TeamCrest from './TeamCrest.vue'

const props = defineProps<{
  tables: StandingGroup[]
  places?: number[] | null
  /** When set, each place is coloured by its stage instead of one shared list. */
  stageOf?: (groupId: string | null, place: number) => string
}>()

function stageId(groupId: string | null, place: number) {
  return props.stageOf?.(groupId, place) || ''
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

function circleTip(groupId: string | null, place: number) {
  if (!desktopTip.value) return undefined
  return stageFullName(stageId(groupId, place)) || undefined
}

function stageName(groupId: string | null, place: number) {
  return stageFullName(stageId(groupId, place)) || undefined
}

function dismissTip(event: KeyboardEvent) {
  ;(event.currentTarget as HTMLElement).classList.add('tip-off')
}

function clearTip(event: FocusEvent) {
  ;(event.currentTarget as HTMLElement).classList.remove('tip-off')
}

const teams = useTeamDirectory()

function clubName(row: { teamId?: string; teamName?: string }) {
  const known = row.teamId ? teams.teams.value[row.teamId]?.name : ''
  const fromRow = (row.teamName || '').trim()
  if (known) return known
  if (fromRow && fromRow !== 'Unknown') return fromRow
  return fromRow || 'Команда'
}
</script>

<template>
  <div class="groups">
    <section v-for="(table, index) in tables" :key="table.id || index" class="group">
      <h3 v-if="table.name">{{ table.name }}</h3>
      <div class="desk">
        <div class="line head">
          <span>#</span>
          <span>Команда</span>
          <span>И</span>
          <span>В</span>
          <span>Н</span>
          <span>П</span>
          <span>Мячи</span>
          <span>О</span>
        </div>
        <RouterLink
          v-for="(row, rowIndex) in table.rows"
          :key="row.teamId"
          class="line"
          :class="{ mark: !stageId(table.id, rowIndex + 1) && advancingRank(rowIndex, places), q: !!stageId(table.id, rowIndex + 1) }"
          :data-qualify="stageId(table.id, rowIndex + 1) || null"
          :to="`/teams/${row.teamId}`"
        >
          <span><i
            class="rank"
            :class="{ ice: !stageId(table.id, rowIndex + 1) && advancingRank(rowIndex, places), q: !!stageId(table.id, rowIndex + 1) }"
            :tabindex="stageId(table.id, rowIndex + 1) ? 0 : undefined"
            :title="circleTip(table.id, rowIndex + 1)"
            :aria-label="stageName(table.id, rowIndex + 1)"
            @keydown.escape.prevent="dismissTip"
            @blur="clearTip"
          >{{ rowIndex + 1 }}<span v-if="stageName(table.id, rowIndex + 1)" class="stage-tip" aria-hidden="true">{{ stageName(table.id, rowIndex + 1) }}</span></i></span>
          <span class="club">
            <TeamCrest :src="teams.logo(row.teamId)" :name="clubName(row)" :size="28" />
            <b>{{ clubName(row) }}</b>
          </span>
          <span>{{ row.played }}</span>
          <span>{{ row.wins }}</span>
          <span>{{ row.draws }}</span>
          <span>{{ row.losses }}</span>
          <span>{{ row.goalsFor }}:{{ row.goalsAgainst }}</span>
          <span class="pts">{{ row.points }}</span>
        </RouterLink>
      </div>
      <div class="phone">
        <RouterLink
          v-for="(row, rowIndex) in table.rows"
          :key="`m-${row.teamId}`"
          class="mrow"
          :class="{ q: !!stageId(table.id, rowIndex + 1) }"
          :data-qualify="stageId(table.id, rowIndex + 1) || null"
          :to="`/teams/${row.teamId}`"
        >
          <i
            class="rank"
            :class="{ ice: !stageId(table.id, rowIndex + 1) && advancingRank(rowIndex, places), q: !!stageId(table.id, rowIndex + 1) }"
            :tabindex="stageId(table.id, rowIndex + 1) ? 0 : undefined"
            :title="circleTip(table.id, rowIndex + 1)"
            :aria-label="stageName(table.id, rowIndex + 1)"
            @keydown.escape.prevent="dismissTip"
            @blur="clearTip"
          >{{ rowIndex + 1 }}<span v-if="stageName(table.id, rowIndex + 1)" class="stage-tip" aria-hidden="true">{{ stageName(table.id, rowIndex + 1) }}</span></i>
          <span class="club">
            <TeamCrest :src="teams.logo(row.teamId)" :name="clubName(row)" :size="28" />
            <span>
              <b>{{ clubName(row) }}</b>
              <small>И {{ row.played }} · В {{ row.wins }} · Н {{ row.draws }} · П {{ row.losses }} · {{ row.goalsFor }}:{{ row.goalsAgainst }}</small>
            </span>
          </span>
          <span class="pts"><b>{{ row.points }}</b><small>очк.</small></span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.groups { display: grid; gap: 1rem; min-width: 0; }
.group h3 {
  margin: 0 0 0.35rem;
  color: var(--navy);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
}
.desk { min-width: 0; }
.line {
  display: grid;
  grid-template-columns: 2.2rem minmax(0, 1fr) 2rem 2rem 2rem 2rem 4.2rem 2.2rem;
  gap: 0.25rem;
  align-items: center;
  min-height: 2.7rem;
  padding: 0.2rem 0.2rem;
  border-bottom: 1px solid var(--line);
  color: inherit;
  text-decoration: none;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.line.head {
  min-height: 2rem;
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.line.mark { background: rgba(76, 180, 229, 0.08); }
.club {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  text-align: left;
}
.club b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.pts { font-weight: 800; color: var(--navy); }
.rank {
  position: relative;
  width: 1.45rem;
  height: 1.45rem;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  font-style: normal;
  font-weight: 800;
  font-size: 0.75rem;
  color: var(--muted);
  background: #eef2f6;
}
.rank.ice { background: var(--ice); color: var(--navy); }
.line, .mrow { color: #00205b; }
.line.q, .mrow.q {
  background: var(--q-row);
  box-shadow: inset var(--q-bar-width, 0px) 0 0 var(--q-bar, transparent);
}
.rank.q {
  background: var(--q-circle);
  color: var(--q-digit, #00205b);
  border: var(--q-stroke-width, 0px) solid var(--q-stroke, transparent);
}
.phone { display: none; }
@media (max-width: 720px) {
  .desk { display: none; }
  .phone { display: grid; }
  .mrow {
    display: grid;
    grid-template-columns: 1.6rem minmax(0, 1fr) auto;
    gap: 0.45rem;
    align-items: center;
    padding: 0.55rem 0;
    border-bottom: 1px solid var(--line);
    color: inherit;
    text-decoration: none;
    min-width: 0;
  }
  .mrow .club b { white-space: normal; }
  .mrow small { display: block; color: #00205b; font-size: 0.72rem; }
  .mrow .pts { display: grid; justify-items: end; color: var(--navy); line-height: 1.1; }
  .mrow .pts small { font-weight: 700; }
  .rank.q {
    width: 1.2rem;
    height: 1.2rem;
    border-radius: 50%;
    background: var(--q-circle);
    color: var(--q-digit, #00205b);
    border: var(--q-stroke-width, 0px) solid var(--q-stroke, transparent);
  }
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
