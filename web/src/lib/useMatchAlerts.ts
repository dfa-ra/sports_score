import { onUnmounted, ref, watch } from 'vue'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useFavorites } from '../stores/favorites'
import { registeredName } from './playerTitle'
import { useTeamDirectory } from './useTeamDirectory'

const STORAGE_KEY = 'kb_goal_alerts'

export type GoalAlertChoice = 'granted' | 'denied' | 'off' | 'default'
export type MatchAlertKind = 'start' | 'goal' | 'finish'

export interface LiveMatchFrame {
  type?: string
  matchId?: string
  homeScore?: number
  awayScore?: number
  lastEvent?: {
    id?: string
    eventType?: string
    voided?: boolean
    playerName?: string | null
    playerFirstName?: string | null
    playerLastName?: string | null
    firstName?: string | null
    lastName?: string | null
    timestamp?: string | null
  } | null
}

export function readGoalAlertChoice(): GoalAlertChoice {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw === 'granted' || raw === 'denied' || raw === 'off') return raw
  } catch {
    /* private mode */
  }
  return 'default'
}

function writeGoalAlertChoice(choice: GoalAlertChoice) {
  try {
    localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    /* private mode */
  }
}

export function classifyMatchAlert(live: LiveMatchFrame): MatchAlertKind | null {
  if (live.type === 'MATCH_EVENT_VOIDED') return null
  if (live.type === 'MATCH_STARTED') return 'start'
  if (live.type === 'MATCH_FINISHED') return 'finish'
  if (live.type === 'MATCH_EVENT' && live.lastEvent?.eventType === 'GOAL' && !live.lastEvent.voided) {
    return 'goal'
  }
  return null
}

function alertTitle(kind: MatchAlertKind, playerName?: string | null) {
  if (kind === 'start') return 'Матч начался'
  if (kind === 'finish') return 'Матч завершён'
  const who = playerName?.trim()
  return who ? `Гол · ${who}` : 'Гол'
}

function scoreLine(home: string, away: string, homeScore: number, awayScore: number) {
  return `${home} ${homeScore}:${awayScore} ${away}`
}

export function useMatchAlerts() {
  const fav = useFavorites()
  const auth = useAuthStore()
  const teams = useTeamDirectory()
  const enabled = ref(false)
  const choice = ref<GoalAlertChoice>('default')
  const names = new Map<string, { home: string; away: string }>()
  const seen = new Set<string>()
  const subs = new Map<string, StompSubscription>()
  let client: Client | null = null

  function browserAllows() {
    return typeof Notification !== 'undefined' && Notification.permission === 'granted'
  }

  function syncEnabled() {
    choice.value = readGoalAlertChoice()
    enabled.value = choice.value === 'granted' && browserAllows()
  }

  async function requestAlerts() {
    if (typeof Notification === 'undefined') return
    if (enabled.value) {
      writeGoalAlertChoice('off')
      syncEnabled()
      stopClient()
      return
    }
    let permission = Notification.permission
    if (permission !== 'granted') {
      try {
        permission = (await Notification.requestPermission()) || Notification.permission
      } catch {
        permission = Notification.permission
      }
    }
    if (permission === 'granted') writeGoalAlertChoice('granted')
    else if (permission === 'denied') writeGoalAlertChoice('denied')
    syncEnabled()
    if (enabled.value) ensureClient()
  }

  async function namesFor(matchId: string) {
    const cached = names.get(matchId)
    if (cached) return cached
    try {
      await teams.load()
      const { data } = await api.get(`/matches/${matchId}`)
      const row = {
        home: teams.fullName(data.homeTeamId),
        away: teams.fullName(data.awayTeamId),
      }
      names.set(matchId, row)
      return row
    } catch {
      return { home: 'Хозяева', away: 'Гости' }
    }
  }

  function show(title: string, body: string, tag: string) {
    if (!enabled.value || !browserAllows()) return
    try {
      new Notification(title, { body, tag, lang: 'ru', icon: '/favicon.svg' })
    } catch {
      /* permission revoked or insecure context — stay quiet */
    }
  }

  async function onLive(live: LiveMatchFrame) {
    const kind = classifyMatchAlert(live)
    const matchId = live.matchId
    if (!kind || !matchId || !fav.hasMatch(matchId)) return
    const eventId = live.lastEvent?.id
      || `${live.homeScore ?? 0}-${live.awayScore ?? 0}-${live.lastEvent?.timestamp ?? ''}`
    const key = kind === 'goal' ? `goal:${matchId}:${eventId}` : `${kind}:${matchId}`
    if (seen.has(key)) return
    seen.add(key)
    const pair = await namesFor(matchId)
    const homeScore = live.homeScore ?? 0
    const awayScore = live.awayScore ?? 0
    show(alertTitle(kind, registeredName(live.lastEvent)), scoreLine(pair.home, pair.away, homeScore, awayScore), key)
  }

  function handleFrame(matchId: string, message: IMessage) {
    try {
      const live = JSON.parse(message.body) as LiveMatchFrame
      void onLive({ ...live, matchId: live.matchId || matchId })
    } catch {
      /* malformed frame */
    }
  }

  function resubscribe() {
    if (!client?.connected) return
    const wanted = new Set(fav.matches)
    for (const [id, sub] of subs) {
      if (!wanted.has(id)) {
        sub.unsubscribe()
        subs.delete(id)
      }
    }
    for (const id of wanted) {
      if (subs.has(id)) continue
      subs.set(id, client.subscribe(`/topic/matches/${id}`, (message) => handleFrame(id, message)))
      void namesFor(id)
    }
  }

  function ensureClient() {
    if (!enabled.value) return
    if (!client) {
      client = new Client({
        webSocketFactory: () => new SockJS('/ws') as WebSocket,
        connectHeaders: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
        reconnectDelay: 5000,
        onConnect: () => resubscribe(),
      })
      client.activate()
      return
    }
    if (client.connected) resubscribe()
  }

  function stopClient() {
    for (const sub of subs.values()) {
      try {
        sub.unsubscribe()
      } catch {
        /* socket already closed */
      }
    }
    subs.clear()
    const current = client
    client = null
    void current?.deactivate()
  }

  watch(() => fav.matches.slice(), () => {
    if (!enabled.value) return
    ensureClient()
  })

  syncEnabled()
  if (enabled.value) ensureClient()
  onUnmounted(() => stopClient())

  return { enabled, choice, requestAlerts }
}
