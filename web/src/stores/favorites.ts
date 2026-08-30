import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const KEY = 'kb_favorites'

type FavState = {
  teams: string[]
  matches: string[]
}

function read(): FavState {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { teams: [], matches: [] }
    const parsed = JSON.parse(raw) as FavState
    return {
      teams: Array.isArray(parsed.teams) ? parsed.teams : [],
      matches: Array.isArray(parsed.matches) ? parsed.matches : [],
    }
  } catch {
    return { teams: [], matches: [] }
  }
}

export const useFavorites = defineStore('favorites', () => {
  const teams = ref<string[]>(read().teams)
  const matches = ref<string[]>(read().matches)

  function persist() {
    localStorage.setItem(KEY, JSON.stringify({ teams: teams.value, matches: matches.value }))
  }

  function hasTeam(id?: string | null) {
    return !!id && teams.value.includes(id)
  }

  function hasMatch(id?: string | null) {
    return !!id && matches.value.includes(id)
  }

  function toggleTeam(id: string) {
    teams.value = hasTeam(id) ? teams.value.filter((item) => item !== id) : [...teams.value, id]
    persist()
  }

  function toggleMatch(id: string) {
    matches.value = hasMatch(id) ? matches.value.filter((item) => item !== id) : [...matches.value, id]
    persist()
  }

  const count = computed(() => teams.value.length + matches.value.length)

  return { teams, matches, count, hasTeam, hasMatch, toggleTeam, toggleMatch }
})
