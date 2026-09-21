import { computed, ref } from 'vue'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from './errors'

export interface ApplyTeam {
  id: string
  name: string
  captainId?: string
  disbanded?: boolean
}

export function useTournamentApply() {
  const auth = useAuthStore()
  const teams = ref<ApplyTeam[]>([])
  const registeredTeamIds = ref<Set<string>>(new Set())
  const teamId = ref('')
  const pending = ref(false)
  const error = ref('')
  const ok = ref('')

  const canApply = computed(() => auth.hasRole('CAPTAIN') || auth.canManageLeague)
  const availableTeams = computed(() =>
    teams.value.filter((team) => !registeredTeamIds.value.has(team.id))
  )

  async function loadTeams(tournamentId?: string) {
    teams.value = []
    registeredTeamIds.value = new Set()
    teamId.value = ''
    error.value = ''
    ok.value = ''
    if (!auth.isAuthenticated) return

    const requests: Promise<any>[] = [
      api.get('/teams', { params: { size: 200 } }),
    ]
    if (tournamentId) {
      requests.push(api.get(`/tournaments/${tournamentId}/teams`))
    }
    const [teamRes, entryRes] = await Promise.all(requests)

    let mine = (teamRes.data.content ?? []).filter((item: ApplyTeam) => !item.disbanded)
    if (!auth.canManageLeague) {
      if (!auth.hasRole('CAPTAIN')) {
        teams.value = []
        return
      }
      try {
        const me = await api.get('/players/me')
        mine = mine.filter((item: ApplyTeam) => item.captainId === me.data.id)
      } catch {
        mine = []
      }
    }
    teams.value = mine

    const taken = new Set<string>()
    for (const entry of entryRes?.data ?? []) {
      if (entry?.teamId && entry.status !== 'WITHDRAWN' && entry.status !== 'REJECTED') {
        taken.add(entry.teamId)
      }
    }
    registeredTeamIds.value = taken
    const firstFree = mine.find((item: ApplyTeam) => !taken.has(item.id))
    teamId.value = firstFree?.id || mine[0]?.id || ''
  }

  async function apply(tournamentId: string, chosenTeamId?: string) {
    error.value = ''
    ok.value = ''
    const id = chosenTeamId || teamId.value
    if (!id) {
      error.value = 'Выберите команду.'
      return false
    }
    pending.value = true
    try {
      await api.post(`/tournaments/${tournamentId}/teams`, { teamId: id })
      ok.value = 'Заявка ушла. Ждём допуск админа.'
      registeredTeamIds.value = new Set([...registeredTeamIds.value, id])
      const next = teams.value.find((item) => !registeredTeamIds.value.has(item.id))
      teamId.value = next?.id || id
      return true
    } catch (e: any) {
      error.value = apiError(e)
      return false
    } finally {
      pending.value = false
    }
  }

  return {
    teams,
    availableTeams,
    registeredTeamIds,
    teamId,
    pending,
    error,
    ok,
    canApply,
    loadTeams,
    apply,
  }
}
