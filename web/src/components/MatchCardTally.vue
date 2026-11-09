<script setup lang="ts">
import { computed } from 'vue'
import { cardTally, type MarkSource } from '../lib/eventMark'

const props = defineProps<{
  events: Array<MarkSource & { voided?: boolean | null; teamId?: string | null }>
  homeTeamId?: string | null
}>()

const tally = computed(() => cardTally(props.events, props.homeTeamId))
</script>

<template>
  <section class="tally">
    <h2>Карточки</h2>
    <div class="line">
      <strong>{{ tally.homeYellow }}</strong>
      <span>Жёлтые</span>
      <strong>{{ tally.awayYellow }}</strong>
    </div>
    <div class="line">
      <strong>{{ tally.homeRed }}</strong>
      <span>Красные</span>
      <strong>{{ tally.awayRed }}</strong>
    </div>
  </section>
</template>

<style scoped>
.tally { display: grid; gap: 0.15rem; }
h2 { font-size: 1.05rem; margin: 0 0 0.35rem; }
.line {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.75rem;
  align-items: center;
  padding: 0.38rem 0;
  border-top: 1px solid var(--line);
}
.line strong {
  font-variant-numeric: tabular-nums;
  color: var(--navy);
  font-weight: 800;
}
.line strong:first-child { text-align: right; }
.line strong:last-child { text-align: left; }
.line span {
  text-align: center;
  color: var(--muted);
  font-size: 0.82rem;
  font-weight: 700;
}
</style>
