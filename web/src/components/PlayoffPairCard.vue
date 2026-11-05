<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import TeamCrest from './TeamCrest.vue'
import {
  footerView,
  pairAria,
  scoreView,
  seedChip,
  sideKind,
  sideName,
  type PlayoffSlot,
} from '../lib/playoffGrid'
import { isThirdPlaceSlot, THIRD_PLACE_SLOT_LABEL } from '../lib/playoffLabel'

const props = defineProps<{
  slot: PlayoffSlot
  previousTitle?: string | null
  firstRound?: boolean
  compact?: boolean
  final?: boolean
  lit?: string[]
  canHover?: boolean
  logo?: (id?: string | null) => string
}>()

const emit = defineEmits<{ hover: [id: string | null]; focus: []; blur: [] }>()

const live = computed(() => props.slot.matchStatus === 'LIVE' || props.slot.matchStatus === 'PAUSED')
const thirdPlace = computed(() => isThirdPlaceSlot(props.slot))
const showFinal = computed(() => !!props.final && !thirdPlace.value)
const footer = computed(() => footerView(props.slot))
const aria = computed(() => {
  const pair = pairAria(props.slot, props.previousTitle)
  return thirdPlace.value ? `${THIRD_PLACE_SLOT_LABEL}. ${pair}` : pair
})
const showSeed = computed(() => !!props.firstRound && !props.compact)

function teamId(home: boolean) {
  return home ? props.slot.homeTeamId : props.slot.awayTeamId
}

function kind(home: boolean) {
  return sideKind(props.slot, home)
}

function name(home: boolean) {
  return sideName(props.slot, home, props.previousTitle)
}

function score(home: boolean) {
  return scoreView(props.slot, home)
}

function seed(home: boolean) {
  if (!showSeed.value) return ''
  return seedChip(
    home ? props.slot.homeGroupName : props.slot.awayGroupName,
    home ? props.slot.homePlace : props.slot.awayPlace,
  )
}

function rowLit(home: boolean) {
  const id = teamId(home)
  return !!id && (props.lit ?? []).includes(id)
}
</script>

<template>
  <component
    :is="slot.matchId ? RouterLink : 'div'"
    class="pc"
    :class="{ live, final: showFinal, bronze: thirdPlace, compact, static: !slot.matchId }"
    :to="slot.matchId ? `/matches/${slot.matchId}` : undefined"
    :role="slot.matchId ? undefined : 'group'"
    :aria-label="aria"
    @focus="emit('focus')"
    @blur="emit('blur')"
  >
    <div v-if="thirdPlace" class="fh bronze">{{ THIRD_PLACE_SLOT_LABEL }}</div>
    <div v-else-if="showFinal" class="fh">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3M12 14v4M8 20h8" />
      </svg>
      Финал
    </div>
    <div
      v-for="home in [true, false]"
      :key="home ? 'h' : 'a'"
      class="pr"
      :class="[kind(home), { hl: rowLit(home) }]"
      @mouseenter="teamId(home) && canHover && emit('hover', teamId(home) || null)"
      @mouseleave="canHover && emit('hover', null)"
    >
      <template v-if="kind(home) === 'empty'">
        <span class="ph" aria-hidden="true"></span>
        <span class="pn" :title="name(home)">{{ name(home) }}</span>
      </template>
      <template v-else>
        <TeamCrest :src="logo?.(teamId(home))" :name="name(home)" :size="24" />
        <span class="pn" :title="name(home)">{{ name(home) }}</span>
        <span v-if="seed(home)" class="seed">{{ seed(home) }}</span>
        <span v-else class="seed ghost"></span>
        <span class="ps" :class="{ dash: score(home).dash }">
          {{ score(home).text }}<sup v-if="score(home).pens != null">({{ score(home).pens }})</sup>
        </span>
      </template>
    </div>
    <div class="pf">
      <span v-if="footer.label" class="st" :class="footer.kind">{{ footer.label }}</span>
      <span>{{ footer.right }}</span>
    </div>
  </component>
</template>

<style scoped>
.pc {
  display: block;
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  min-width: 0;
}
a.pc:hover { border-color: var(--ice); color: inherit; }
.pc:focus-visible { outline: 2px solid var(--ice); outline-offset: 2px; }
.pc.static:hover { border-color: var(--line-strong); }
.pc.live {
  border-color: rgba(227, 107, 91, 0.55);
  box-shadow: 0 0 0 3px rgba(227, 107, 91, 0.14), var(--shadow);
}
.pc.live .pf { background: rgba(227, 107, 91, 0.08); }
.pc.live .ps { color: var(--ink); }
.pc.final { border: 2px solid var(--navy); }
.pc.bronze { border: 2px solid #8a5a2b; }
.fh {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.45rem 0.7rem;
  background: var(--navy);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.fh.bronze {
  background: #8a5a2b;
  letter-spacing: 0.02em;
  text-transform: none;
}
.fh svg {
  width: 16px;
  height: 16px;
  stroke: var(--ice);
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pr {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto auto;
  gap: 0.55rem;
  align-items: center;
  padding: 0 0.7rem;
  height: 42px;
  font-weight: 700;
  color: var(--ink);
  font-size: 0.9375rem;
  min-width: 0;
}
.pr + .pr { border-top: 1px solid var(--line); }
.pr :deep(.team-crest) { width: 24px; height: 24px; border-radius: 50%; }
.pn {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.seed {
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 700;
  background: #eef3f8;
  border-radius: 6px;
  padding: 0 0.35rem;
  line-height: 1.5;
}
.seed.ghost { background: transparent; padding: 0; }
.ps {
  font-weight: 800;
  font-size: 1.0625rem;
  min-width: 20px;
  text-align: right;
}
.ps sup {
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 700;
  margin-left: 2px;
  position: relative;
  top: -0.35em;
}
.ps.dash { color: var(--muted); font-weight: 600; }
.pr.win {
  box-shadow: inset 3px 0 0 var(--ice);
  background: linear-gradient(90deg, rgba(76, 180, 229, 0.1), transparent 70%);
}
.pr.win .pn { font-weight: 800; }
.pr.win .ps { color: var(--navy); }
.pr.lose { color: var(--muted); font-weight: 600; }
.pr.lose :deep(.team-crest) { opacity: 0.5; }
.pr.lose .ps,
.pr.lose .pn { color: var(--muted); font-weight: 600; }
.pr.empty {
  grid-template-columns: 24px minmax(0, 1fr);
  color: var(--muted);
  font-weight: 600;
}
.ph {
  box-sizing: border-box;
  width: 24px;
  height: 24px;
  min-width: 24px;
  min-height: 24px;
  max-width: 24px;
  max-height: 24px;
  padding: 0;
  margin: 0;
  border-radius: 50%;
  border: 1.5px dashed var(--line-strong);
  background: transparent;
  display: block;
  justify-self: center;
  align-self: center;
}
.pr.empty .pn { font-size: 0.875rem; }
.pc.bronze .pr.empty .pn { font-size: 0.75rem; letter-spacing: -0.01em; }
.pc.compact .pr {
  height: auto;
  min-height: 34px;
  max-height: 36px;
  overflow: hidden;
}
.pc.compact .pn,
.pc.compact .pr.empty .pn {
  font-size: 13px;
  line-height: 16px;
  max-height: 32px;
  white-space: normal;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: normal;
  overflow-wrap: normal;
  hyphens: manual;
}
.pr.hl {
  background: rgba(0, 32, 91, 0.07) !important;
  box-shadow: inset 3px 0 0 var(--navy) !important;
}
.pr.hl .pn { color: var(--navy); }
.pf {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.4rem 0.7rem;
  background: #f6f9fc;
  border-top: 1px solid var(--line);
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 600;
  min-height: 32px;
}
.st {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border-radius: 999px;
  padding: 0.05rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  line-height: 1.5;
}
.st.live { background: rgba(227, 107, 91, 0.14); color: #b4402f; }
.st.live::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--danger);
}
.st.soon { background: rgba(76, 180, 229, 0.16); color: #1b5f8c; }
.st.done { background: #eef2f6; color: var(--muted); text-transform: none; letter-spacing: 0; }
@media (prefers-reduced-motion: no-preference) {
  .st.live::before { animation: po-pulse 1.6s infinite; }
}
@keyframes po-pulse {
  70% { box-shadow: 0 0 0 7px rgba(227, 107, 91, 0); }
  100% { box-shadow: 0 0 0 0 rgba(227, 107, 91, 0); }
}
</style>
