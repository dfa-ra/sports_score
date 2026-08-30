export function matchOutcome(match: {
  status?: string
  homeTeamId?: string
  awayTeamId?: string
  homeScore?: number
  awayScore?: number
}, teamId?: string | null) {
  if (!teamId || match.status !== 'FINISHED') return null
  const home = teamId === match.homeTeamId
  if (!home && teamId !== match.awayTeamId) return null
  const scored = home ? Number(match.homeScore) : Number(match.awayScore)
  const conceded = home ? Number(match.awayScore) : Number(match.homeScore)
  if (scored > conceded) return 'WIN'
  if (scored < conceded) return 'LOSS'
  return 'DRAW'
}

export function shortKickoff(value?: string | number | Date | null, status?: string) {
  if (status === 'LIVE' || status === 'PAUSED') return 'LIVE'
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  if (status === 'FINISHED' || status === 'CANCELLED') return `${day}.${month}.`
  return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}
