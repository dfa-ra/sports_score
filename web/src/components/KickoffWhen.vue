<script setup lang="ts">
import { computed } from 'vue'
import { formatClock } from '../lib/format'
import { eventMinute, kickoffClock, kickoffDayMonth, matchInstant } from '../lib/match'
import { useMatchClock } from '../lib/useMatchClock'

const props = withDefaults(defineProps<{
  at?: string | number | Date | null
  startedAt?: string | number | Date | null
  status?: string
  minute?: number | null
  align?: 'center' | 'start'
  gameTimeSeconds?: number | null
  clockRunningSince?: string | null
  period?: number | null
  periodLengthSeconds?: number | null
}>(), {
  align: 'center',
})

const clockSource = computed(() => ({
  status: props.status,
  gameTimeSeconds: props.gameTimeSeconds,
  clockRunningSince: props.clockRunningSince,
  period: props.period,
  periodLengthSeconds: props.periodLengthSeconds,
}))
const { elapsed, kickoff } = useMatchClock(clockSource)

const isLive = computed(() => props.status === 'LIVE' || props.status === 'PAUSED')
const hasClock = computed(() =>
  props.gameTimeSeconds != null || !!props.clockRunningSince || props.period != null,
)
const dateLabel = computed(() => kickoffDayMonth(matchInstant({
  status: props.status,
  scheduledAt: props.at,
  startedAt: props.startedAt,
})))
const timeLabel = computed(() => {
  if (props.status === 'FINISHED') return 'Завершён'
  if (!isLive.value) {
    const clock = kickoffClock(props.at)
    if (clock) return clock
    if (props.status === 'SCHEDULED' || !props.status) return 'Дата уточняется'
    return '—'
  }
  const fromClock = eventMinute(kickoff.value, props.period, props.periodLengthSeconds)
  const minute = hasClock.value ? fromClock : props.minute
  if (minute != null && minute > 0) return `${minute}'`
  if (hasClock.value && (elapsed.value > 0 || kickoff.value > 0)) return formatClock(kickoff.value)
  if (props.minute != null && props.minute > 0) return `${props.minute}'`
  return props.status === 'PAUSED' ? 'Пауза' : 'LIVE'
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
