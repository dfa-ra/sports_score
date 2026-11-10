/** Stage colours shared by the admin rows, the public table and the legend. */

export type QualifyStageId = 'R16' | 'R8' | 'QF' | 'SF' | 'F' | 'SILVER' | 'OUT'

export type StageLine = {
  stage: QualifyStageId
  direct: number
  fromPrev: number
  total: number
  powerOfTwo: boolean
}

export type QualifyMark = {
  groupId?: string | null
  place: number
  stage: string
}

const PIPE: QualifyStageId[] = ['R16', 'R8', 'QF', 'SF']
const ALLOWED = [2, 4, 8, 16]

export const QUALIFY_STAGES: { id: QualifyStageId; label: string; color: string; ink: string }[] = [
  { id: 'R16', label: '1/16', color: '#7b61ff', ink: '#fff' },
  { id: 'R8', label: '1/8', color: '#4cb4e5', ink: '#102033' },
  { id: 'QF', label: '1/4', color: '#2f9e6b', ink: '#fff' },
  { id: 'SF', label: '1/2', color: '#e0a106', ink: '#102033' },
  { id: 'SILVER', label: 'Серебряная лига', color: '#8d97a8', ink: '#102033' },
  { id: 'OUT', label: 'Выбывает', color: '#d5dbe3', ink: '#102033' },
]

const BY_ID = new Map(QUALIFY_STAGES.map((item) => [item.id, item]))

export function stageMeta(id?: string | null) {
  if (!id) return null
  return BY_ID.get(id as QualifyStageId) || null
}

export function stageLabel(id?: string | null) {
  if (id === 'F') return 'Финал'
  return stageMeta(id)?.label || ''
}

export function teamsWord(count: number) {
  const tail = Math.abs(count) % 100
  const digit = tail % 10
  if (tail > 10 && tail < 20) return 'команд'
  if (digit === 1) return 'команда'
  if (digit >= 2 && digit <= 4) return 'команды'
  return 'команд'
}

export function winnersWord(count: number) {
  const tail = Math.abs(count) % 100
  const digit = tail % 10
  if (tail > 10 && tail < 20) return 'победителей'
  if (digit === 1) return 'победитель'
  if (digit >= 2 && digit <= 4) return 'победителя'
  return 'победителей'
}

export function groupHeading(name: string, count: number) {
  const trimmed = String(name || '').trim()
  const letter = trimmed.match(/([A-Za-zА-Яа-яЁё])$/)
  const title = letter ? `ГРУППА ${letter[1].toUpperCase()}` : (trimmed || 'ГРУППА').toUpperCase()
  return `${title} · ${count} ${teamsWord(count)}`
}

function allowed(total: number) {
  return ALLOWED.includes(total)
}

function suggested(total: number) {
  return ALLOWED.find((size) => size >= total) ?? ALLOWED[ALLOWED.length - 1]
}

export function qualifyLines(direct: Partial<Record<QualifyStageId, number>>): StageLine[] {
  const rows: StageLine[] = []
  let incoming = 0
  let started = false
  let chainOk = true
  let closed = false
  for (const stage of PIPE) {
    const count = direct[stage] || 0
    if (!started && count === 0 && incoming === 0) continue
    started = true
    const total = count + incoming
    const ok = chainOk && allowed(total)
    rows.push({ stage, direct: count, fromPrev: incoming, total, powerOfTwo: ok })
    if (!ok) chainOk = false
    if (ok && total === 2) {
      closed = true
      break
    }
    incoming = Math.floor(total / 2)
  }
  if (started && !closed) {
    const total = incoming
    rows.push({
      stage: 'F',
      direct: 0,
      fromPrev: incoming,
      total,
      powerOfTwo: chainOk && allowed(total),
    })
  }
  return rows
}

export function powerMessage(line: StageLine, previous?: QualifyStageId | null) {
  const expect = suggested(line.total)
  const prev = previous ? stageLabel(previous) : 'прошлого круга'
  return `В ${stageLabel(line.stage)} должно быть ${expect} ${teamsWord(expect)}: ${line.direct} напрямую + ${line.fromPrev} ${winnersWord(line.fromPrev)} ${prev}`
}

export function firstPowerError(lines: StageLine[]) {
  const index = lines.findIndex((line) => !line.powerOfTwo)
  if (index < 0) return ''
  const previous = index === 0 ? null : lines[index - 1].stage
  return powerMessage(lines[index], previous)
}

export type PlaceSpan = { groupId: string; placeFrom: number; placeTo: number }

/** Same sentence as the server when a place is outside 1..group size. */
export function groupToken(name: string) {
  const trimmed = String(name || '').trim()
  const letter = trimmed.match(/([A-Za-zА-Яа-яЁё])$/)
  return letter ? letter[1].toUpperCase() : trimmed
}

export function placeOutOfRange(
  groups: { id: string; name: string; teamCount: number }[],
  rules: PlaceSpan[],
) {
  const byId = new Map(groups.map((group) => [group.id, group]))
  for (const rule of rules) {
    const group = byId.get(rule.groupId)
    if (!group) continue
    if (rule.placeFrom < 1 || rule.placeTo < rule.placeFrom) {
      return `Диапазон мест ${rule.placeFrom}–${rule.placeTo} в группе «${group.name}» перепутан`
    }
    if (rule.placeFrom <= group.teamCount && rule.placeTo > group.teamCount) {
      return `В группе ${groupToken(group.name)} теперь ${group.teamCount} ${teamsWord(group.teamCount)}, а правило до места ${rule.placeTo}`
    }
    if (rule.placeTo > group.teamCount) {
      return `Место ${rule.placeTo} в группе «${group.name}» больше числа команд (${group.teamCount})`
    }
  }
  return ''
}

/** A round of exactly two teams is the final, even when that round was picked as 1/2. */
export function summaryTitle(line: StageLine, lines: StageLine[], index: number) {
  const closing = index === lines.length - 1 && line.total === 2 && line.powerOfTwo
  if (line.stage === 'F' || closing) return 'Финал'
  return stageMeta(line.stage)?.label || ''
}

/** Legend entries that actually appear, in bracket order. The final is not a direct place. */
export function legendItems(marks: QualifyMark[]) {
  const used = new Set(marks.map((mark) => mark.stage).filter((stage) => stage && stage !== 'F'))
  return QUALIFY_STAGES.filter((item) => item.id !== 'F' && used.has(item.id))
}

export function stageMap(marks: QualifyMark[]) {
  const map: Record<string, Record<number, string>> = {}
  for (const mark of marks) {
    if (!mark.groupId || !mark.stage || mark.stage === 'F') continue
    const group = map[mark.groupId] || (map[mark.groupId] = {})
    group[mark.place] = mark.stage
  }
  return map
}
