import { ref } from 'vue'
import api from '../api/client'

export interface TeamBrief {
  id: string
  name: string
  shortName?: string
  logoUrl?: string
}

const cache = ref<Record<string, TeamBrief>>({})
const loaded = ref(false)

export function useTeamDirectory() {
  async function load(force = false) {
    if (loaded.value && !force) return
    try {
      const { data } = await api.get('/teams', { params: { size: 200, includeDisbanded: true } })
      const next: Record<string, TeamBrief> = {}
      for (const team of data.content ?? []) {
        next[team.id] = team
      }
      cache.value = next
      loaded.value = true
    } catch {
      loaded.value = false
    }
  }

  function name(id?: string | null, fallback = 'Команда') {
    if (!id) return fallback
    return cache.value[id]?.shortName || cache.value[id]?.name || fallback
  }

  function fullName(id?: string | null, fallback = 'Команда') {
    if (!id) return fallback
    return cache.value[id]?.name || fallback
  }

  function logo(id?: string | null) {
    if (!id) return ''
    return cache.value[id]?.logoUrl || ''
  }

  function logoByName(value?: string | null) {
    if (!value) return ''
    const hit = Object.values(cache.value).find((team) => team.name === value || team.shortName === value)
    return hit?.logoUrl || ''
  }

  return { teams: cache, load, name, fullName, logo, logoByName }
}
