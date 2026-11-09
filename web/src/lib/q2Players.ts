function countLabel(count: number, one: string, few: string, many: string) {
  const abs = Math.abs(count) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}

export function boardParams(tournamentId: string | null, limit: number) {
  const params: { limit: number; tournamentId?: string } = { limit }
  if (tournamentId) params.tournamentId = tournamentId
  return params
}

/** Trim and collapse inner whitespace. Case stays as typed; the server folds it. */
export function playerSearchQuery(raw: string) {
  return raw.replace(/\s+/g, ' ').trim()
}

/** One `q`, fixed page size, next page is page+1. Tournament is sent only when a chip is on. */
export function playerSearchParams(query: string, page: number, tournamentId?: string | null, size = 10) {
  const params: Record<string, string | number> = {
    q: playerSearchQuery(query),
    page: Math.max(0, page),
    size,
    sort: 'lastName,asc',
  }
  if (tournamentId) params.tournamentId = tournamentId
  return params
}

export function highlightParts(name: string, query: string) {
  const needle = playerSearchQuery(query)
  if (!needle) return [{ text: name, hit: false }]
  const lower = name.toLocaleLowerCase('ru')
  const probe = needle.toLocaleLowerCase('ru')
  const parts: { text: string; hit: boolean }[] = []
  let from = 0
  while (from < name.length) {
    const at = lower.indexOf(probe, from)
    if (at < 0) {
      parts.push({ text: name.slice(from), hit: false })
      break
    }
    if (at > from) parts.push({ text: name.slice(from, at), hit: false })
    parts.push({ text: name.slice(at, at + probe.length), hit: true })
    from = at + probe.length
  }
  return parts.filter((part) => part.text)
}

export function goalsAside(count: number | null | undefined) {
  if (typeof count !== 'number' || Number.isNaN(count)) return '–'
  const n = Math.max(0, Math.trunc(count))
  return `${n} ${countLabel(n, 'гол', 'гола', 'голов')}`
}

export function unitWord(count: number, kind: 'goals' | 'assists') {
  const n = Math.max(0, Math.trunc(count))
  if (kind === 'goals') return countLabel(n, 'гол', 'гола', 'голов')
  return countLabel(n, 'передача', 'передачи', 'передач')
}

/** Source case for the /players sheet title. Global h2 styles uppercase it. */
export function leaderBoardTitle(mode: 'goals' | 'assists') {
  if (mode === 'assists') return 'Лидеры по передачам'
  return 'Бомбардиры'
}

function numeric(value: unknown): number | null {
  if (typeof value === 'number' && !Number.isNaN(value)) return value
  if (typeof value === 'string' && value.trim() && !Number.isNaN(Number(value))) return Number(value)
  return null
}

export function goalsFromCard(data: unknown): number | null {
  if (!data || typeof data !== 'object') return null
  const stats = (data as { statistics?: unknown }).statistics
  if (stats && typeof stats === 'object') {
    const goals = numeric((stats as { goals?: unknown }).goals)
    if (goals != null) return Math.max(0, Math.trunc(goals))
  }
  const history = (data as { matchHistory?: unknown }).matchHistory
  if (!Array.isArray(history)) return null
  return history.reduce((sum, row) => {
    const goals = row && typeof row === 'object' ? numeric((row as { goals?: unknown }).goals) : null
    return sum + (goals == null ? 0 : Math.max(0, Math.trunc(goals)))
  }, 0)
}

export function teamFromCard(data: unknown): { name: string; logo: string } | null {
  if (!data || typeof data !== 'object') return null
  const team = (data as { team?: unknown }).team
  if (!team || typeof team !== 'object') return null
  const name = String((team as { name?: unknown }).name || '').trim()
  const logo = String((team as { logoUrl?: unknown }).logoUrl || '').trim()
  if (!name && !logo) return null
  return { name, logo }
}
