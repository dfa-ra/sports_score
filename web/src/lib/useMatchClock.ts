import { computed, onMounted, onUnmounted, ref, type Ref } from 'vue'

export function useMatchClock(match: Ref<any>) {
  const now = ref(Date.now())
  let timer = 0

  onMounted(() => {
    timer = window.setInterval(() => {
      now.value = Date.now()
    }, 200)
  })

  onUnmounted(() => window.clearInterval(timer))

  const cap = computed(() => match.value?.periodLengthSeconds ?? 1200)

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

  return { elapsed, remaining, expired, cap }
}
