import { computed, onMounted, onUnmounted, ref, type Ref } from 'vue'
import { eventMinute } from './matchMinute'

export function useMatchClock(match: Ref<any>) {
  const now = ref(Date.now())
  let timer = 0

  onMounted(() => {
    timer = window.setInterval(() => {
      now.value = Date.now()
    }, 200)
  })

  onUnmounted(() => window.clearInterval(timer))

  const cap = computed(() => {
    const value = match.value?.periodLengthSeconds
    return value > 0 ? value : 900
  })

  const elapsed = computed(() => {
    const current = match.value
    if (!current) return 0
    let base = current.gameTimeSeconds ?? 0
    if (current.status === 'LIVE' && current.clockRunningSince) {
      base += Math.floor((now.value - new Date(current.clockRunningSince).getTime()) / 1000)
    }
    return Math.min(Math.max(0, base), cap.value)
  })

  const remaining = computed(() => Math.max(0, cap.value - elapsed.value))
  const expired = computed(() => remaining.value <= 0 && (match.value?.status === 'LIVE' || match.value?.status === 'PAUSED'))
  const kickoff = computed(() => {
    const period = Math.max(1, Number(match.value?.period) || 1)
    return (period - 1) * cap.value + elapsed.value
  })

  /** Same kickoff minute as the match list. The pad still uses the hall countdown. */
  const minute = computed(() => {
    const current = match.value
    if (!current) return null
    if (current.status !== 'LIVE' && current.status !== 'PAUSED') return null
    if (current.period == null && current.gameTimeSeconds == null && current.clockRunningSince == null) return null
    const period = Math.max(1, Number(current.period) || 1)
    return eventMinute((period - 1) * cap.value + elapsed.value, period, cap.value)
  })

  return { elapsed, remaining, expired, cap, kickoff, minute }
}
