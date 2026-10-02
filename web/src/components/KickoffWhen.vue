<script setup lang="ts">
import { computed } from 'vue'
import { kickoffClock, kickoffDayMonth } from '../lib/match'

const props = withDefaults(defineProps<{
  at?: string | number | Date | null
  status?: string
  minute?: number | null
  align?: 'center' | 'start'
}>(), {
  align: 'center',
})

const isLive = computed(() => props.status === 'LIVE' || props.status === 'PAUSED')
const dateLabel = computed(() => kickoffDayMonth(props.at))
const timeLabel = computed(() => {
  if (isLive.value) {
    if (props.minute == null) return props.status === 'PAUSED' ? 'Пауза' : 'LIVE'
    return `${props.minute}'`
  }
  return kickoffClock(props.at) || '—'
})
</script>

<template>
  <span
    class="kickoff"
    :class="{ live: isLive, paused: status === 'PAUSED', start: align === 'start' }"
    :title="status === 'PAUSED' ? 'Пауза' : undefined"
  >
    <span v-if="dateLabel" class="kick-date">{{ dateLabel }}</span>
    <span class="kick-time">{{ timeLabel }}</span>
  </span>
</template>

<style scoped>
.kickoff {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.15;
  font-size: 0.72rem;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.kickoff.start { align-items: flex-start; }
.kick-date { font-weight: 700; }
.kickoff.live .kick-time { color: var(--ice); font-weight: 800; }
.kickoff.paused .kick-time { color: var(--muted); font-weight: 700; }
</style>
