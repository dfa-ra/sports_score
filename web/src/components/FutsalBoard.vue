<script setup lang="ts">
import { computed, toRef } from 'vue'
import { formatClock } from '../lib/format'
import { buildFutsalBoard } from '../lib/futsalBoard'
import { useMatchClock } from '../lib/useMatchClock'

const props = defineProps<{
  match: any
  events: any[]
  homeLabel: string
  awayLabel: string
  interactive?: boolean
  pending?: boolean
  live?: boolean
}>()

defineEmits<{
  foul: [teamId: string]
  timeout: [teamId: string]
}>()

const { elapsed } = useMatchClock(toRef(props, 'match'))
const board = computed(() => buildFutsalBoard(props.match, props.events ?? [], elapsed.value))
const sides = computed(() => {
  const labels = [props.homeLabel, props.awayLabel]
  return board.value.teams.map((team, index) => ({
    ...team,
    label: labels[index] || 'Команда',
    away: index === 1,
  }))
})
const scopeLine = computed(() =>
  board.value.foulScope === 'PERIOD'
    ? 'Счёт фолов и тайм-аут — в этом тайме'
    : 'Таймов нет — фолы и тайм-аут на весь матч',
)
</script>

<template>
  <section v-if="match?.sportCode === 'FUTSAL'" class="futsal panel">
    <h2>Футзал</h2>
    <p class="scope">{{ scopeLine }}</p>
    <div class="cols">
      <article v-for="side in sides" :key="side.teamId" class="side" :class="{ away: side.away }">
        <strong>{{ side.label }}</strong>
        <p class="count">Фолы {{ side.fouls }}</p>
        <p v-if="side.tenMeters" class="note">10 метров</p>
        <p v-if="side.shortHanded" class="short">Меньше {{ formatClock(side.shortHandedRemainingSeconds) }}</p>
        <p v-if="!interactive" class="timeout-state">
          {{ side.timeoutAvailable ? 'Тайм-аут есть' : 'Тайм-аут взят' }}
        </p>
        <div v-else class="actions">
          <button class="btn secondary" type="button" :disabled="pending || !live" @click="$emit('foul', side.teamId)">Фол</button>
          <button
            class="btn secondary"
            type="button"
            :disabled="pending || !live || !side.timeoutAvailable"
            @click="$emit('timeout', side.teamId)"
          >Тайм-аут</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
h2 { font-size: 1.05rem; margin: 0; }
.scope { margin: 0.15rem 0 0.7rem; color: var(--muted); font-size: 0.78rem; }
.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}
.side { display: grid; gap: 0.35rem; align-content: start; min-width: 0; }
.side.away { text-align: right; justify-items: end; }
.side strong {
  font-size: 0.85rem;
  line-height: 1.25;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.count { margin: 0; font-weight: 800; font-variant-numeric: tabular-nums; }
.note { margin: 0; color: var(--danger); font-weight: 800; font-size: 0.85rem; }
.short { margin: 0; font-weight: 800; color: var(--navy); font-variant-numeric: tabular-nums; }
.timeout-state { margin: 0; color: var(--muted); font-size: 0.85rem; }
.actions { display: grid; gap: 0.4rem; width: 100%; }
.actions .btn { width: 100%; }
</style>
