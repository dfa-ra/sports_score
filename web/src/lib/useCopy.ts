import { ref } from 'vue'

export function useCopy(resetMs = 1600) {
  const copied = ref(false)
  let timer: number | undefined

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value)
      copied.value = true
      window.clearTimeout(timer)
      timer = window.setTimeout(() => {
        copied.value = false
      }, resetMs)
      return true
    } catch {
      copied.value = false
      return false
    }
  }

  return { copied, copy }
}
