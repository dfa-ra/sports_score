<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import api from '../../api/client'
import { eventDetail, eventLabel, formatClock, labelOf, periodLabel, playerTag } from '../../lib/format'
import { apiError } from '../../lib/errors'
import { useMatchClock } from '../../lib/useMatchClock'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import StatusBadge from '../../components/StatusBadge.vue'
import TeamCrest from '../../components/TeamCrest.vue'

type EventKind = 'GOAL' | 'YELLOW_CARD' | 'RED_CARD'
type Step = 'team' | 'player' | 'assist'

type Sheet = {
  kind: EventKind
  step: Step
  teamId?: string
  player?: any
}

const route = useRoute()
const match = ref<any>(null)
const events = ref<any[]>([])
const homeRoster = ref<any[]>([])
const awayRoster = ref<any[]>([])
const message = ref('')
const error = ref('')
const pending = ref(false)
const sheet = ref<Sheet | null>(null)
const heldClock = ref(false)
const teams = useTeamDirectory()
const { elapsed, remaining, expired, cap } = useMatchClock(match)

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))
const live = computed(() => match.value?.status === 'LIVE' || match.value?.status === 'PAUSED')
const protocol = computed(() => [...events.value].reverse())

function nameOf(player: any) {
  return playerTag(player.displayName || `${player.playerFirstName || ''} ${player.playerLastName || ''}`.trim(), player.jerseyNumber)
}

function rosterOf(teamId?: string) {
  if (!match.value || !teamId) return []
  return teamId === match.value.homeTeamId ? homeRoster.value : awayRoster.value
}

const sheetTitle = computed(() => {
  const current = sheet.value
  if (!current) return ''
  if (current.kind === 'GOAL') {
    if (current.step === 'team') return 'Кто забил?'
    if (current.step === 'player') return 'Кто забил гол?'
    return 'Кто отдал передачу?'
  }
  if (current.kind === 'YELLOW_CARD') {
    return current.step === 'team' ? 'Кому жёлтая?' : 'Кому показать жёлтую?'
  }
  return current.step === 'team' ? 'Кому красная?' : 'Кому показать красную?'
})

const sheetHint = computed(() => {
  const current = sheet.value
  if (!current) return ''
  if (current.kind === 'GOAL' && current.step === 'assist' && current.player) {
    return `Гол: ${nameOf(current.player)}. Если паса не было — пропустите.`
  }
  if (current.step === 'team') return heldClock.value ? 'Часы стоят. Сначала команда.' : 'Сначала команда.'
  return heldClock.value ? 'Часы стоят. Выберите игрока из заявки.' : 'Выберите игрока из заявки.'
})

async function reload() {
  await teams.load()
  const id = route.params.id
  const [m, e] = await Promise.all([
    api.get(`/matches/${id}`),
    api.get(`/matches/${id}/events`),
  ])
  match.value = m.data
  events.value = e.data
  const [home, away] = await Promise.all([
    api.get(`/teams/${m.data.homeTeamId}/members`),
    api.get(`/teams/${m.data.awayTeamId}/members`),
  ])
  homeRoster.value = home.data
  awayRoster.value = away.data
}

async function refreshMatch() {
  const { data } = await api.get(`/matches/${route.params.id}`)
  match.value = data
}

async function holdClock() {
  if (heldClock.value || match.value?.status !== 'LIVE') return
  try {
    await api.post(`/referee/matches/${route.params.id}/pause`)
    heldClock.value = true
    await refreshMatch()
  } catch {
    heldClock.value = false
  }
}

async function releaseClock() {
  if (!heldClock.value) return
  heldClock.value = false
  try {
    if (match.value?.status === 'PAUSED') {
      await api.post(`/referee/matches/${route.params.id}/resume`)
    }
    await refreshMatch()
  } catch {
    /* keep going — the referee can hit Продолжить */
  }
}

async function closeSheet() {
  sheet.value = null
  await releaseClock()
}

async function action(path: string) {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    message.value = path === 'finish' ? 'Матч завершён.' : 'Готово.'
  } catch (e: any) {
    error.value = apiError(e, 'Действие не прошло.')
  } finally {
    pending.value = false
  }
}

async function addEvent(payload: Record<string, unknown>) {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/events`, payload)
    sheet.value = null
    message.value = 'В протоколе.'
    await releaseClock()
    await reload()
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось записать событие')
  } finally {
    pending.value = false
  }
}

async function openSheet(kind: EventKind) {
  error.value = ''
  if (!live.value) {
    error.value = 'Сначала стартуйте матч.'
    return
  }
  await holdClock()
  sheet.value = { kind, step: 'team' }
}

function pickTeam(teamId: string) {
  if (!sheet.value) return
  sheet.value = { ...sheet.value, teamId, step: 'player' }
}

function pickPlayer(player: any) {
  const current = sheet.value
  if (!current?.teamId) return
  if (current.kind === 'GOAL') {
    sheet.value = { ...current, player, step: 'assist' }
    return
  }
  addEvent({ eventType: current.kind, teamId: current.teamId, playerId: player.playerId })
}

function confirmGoal(assistPlayerId?: string | null) {
  const current = sheet.value
  if (!current?.teamId || !current.player) return
  addEvent({
    eventType: 'GOAL',
    teamId: current.teamId,
    playerId: current.player.playerId,
    secondaryPlayerId: assistPlayerId || undefined,
  })
}

function backSheet() {
  const current = sheet.value
  if (!current) return
  if (current.step === 'assist') {
    sheet.value = { ...current, step: 'player', player: undefined }
    return
  }
  if (current.step === 'player') {
    sheet.value = { kind: current.kind, step: 'team' }
    return
  }
  void closeSheet()
}

onMounted(reload)
onUnmounted(() => {
  if (heldClock.value) {
    heldClock.value = false
    api.post(`/referee/matches/${route.params.id}/resume`).catch(() => {})
  }
})
</script>

<template>
  <section v-if="match" class="stack pad">
    <div class="page-title">
      <RouterLink class="back" to="/referee">← К матчам</RouterLink>
      <h1>Пульт</h1>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <StatusBadge :status="match.status" />
      <p class="period">{{ periodLabel(match.period, match.sportCode, match.periodCount) }}</p>
      <div class="clock" :class="{ expired }">{{ formatClock(remaining) }}</div>
      <p class="muted">осталось из {{ formatClock(cap) }} · прошло {{ formatClock(elapsed) }}</p>
      <div class="sides">
        <strong class="club">
          <TeamCrest :src="teams.logo(match.homeTeamId)" :name="homeLabel" :size="28" />
          {{ homeLabel }}
        </strong>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <strong class="club">
          <TeamCrest :src="teams.logo(match.awayTeamId)" :name="awayLabel" :size="28" />
          {{ awayLabel }}
        </strong>
      </div>
    </div>

    <div class="grid controls">
      <button class="btn large success" :disabled="pending || match.status !== 'SCHEDULED'" @click="action('start')">Старт</button>
      <button class="btn large secondary" :disabled="pending || match.status !== 'LIVE'" @click="action('pause')">Пауза</button>
      <button class="btn large secondary" :disabled="pending || match.status !== 'PAUSED'" @click="action('resume')">Продолжить</button>
      <button
        class="btn large"
        :disabled="pending || !live || (match.period ?? 1) >= match.periodCount"
        @click="action('next-period')"
      >Следующий {{ match.sportCode === 'BASKETBALL' ? 'четверть' : 'тайм' }}</button>
      <button class="btn large danger" :disabled="pending || !live" @click="action('finish')">Финиш</button>
    </div>
    <p v-if="expired && live" class="form-ok">Время тайма вышло. Можно свистеть следующий или финиш.</p>

    <div class="grid events">
      <button class="btn large goal" :disabled="pending || !live" @click="openSheet('GOAL')">Гол</button>
      <button class="btn large yellow" :disabled="pending || !live" @click="openSheet('YELLOW_CARD')">Жёлтая</button>
      <button class="btn large danger" :disabled="pending || !live" @click="openSheet('RED_CARD')">Красная</button>
    </div>

    <div class="panel stack">
      <h2>Протокол</h2>
      <p v-if="!protocol.length" class="muted">Пока тихо.</p>
      <div v-for="ev in protocol" :key="ev.id" class="proto" :class="{ voided: ev.voided }">
        <span class="t">{{ formatClock(ev.gameTime) }}</span>
        <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
        <span>{{ eventDetail(ev) || '—' }}</span>
      </div>
    </div>
    <p v-if="message" class="form-ok">{{ message }}</p>
    <p v-if="error" class="form-error">{{ error }}</p>

    <div v-if="sheet" class="overlay" @click.self="closeSheet">
      <div class="popover" role="dialog" aria-modal="true">
        <p class="step">
          {{ sheet.step === 'team' ? '1' : sheet.step === 'player' ? '2' : '3' }}
          /
          {{ sheet.kind === 'GOAL' ? '3' : '2' }}
        </p>
        <h2>{{ sheetTitle }}</h2>
        <p class="muted">{{ sheetHint }}</p>

        <div v-if="sheet.step === 'team'" class="pick-grid">
          <button class="btn large secondary" :disabled="pending" @click="pickTeam(match.homeTeamId)">{{ homeLabel }}</button>
          <button class="btn large secondary" :disabled="pending" @click="pickTeam(match.awayTeamId)">{{ awayLabel }}</button>
        </div>

        <div v-else-if="sheet.step === 'player'" class="chip-col">
          <p v-if="!rosterOf(sheet.teamId).length" class="muted">В заявке никого.</p>
          <button
            v-for="p in rosterOf(sheet.teamId)"
            :key="p.playerId"
            class="btn secondary pick"
            :disabled="pending"
            @click="pickPlayer(p)"
          >{{ nameOf(p) }}</button>
        </div>

        <div v-else class="chip-col">
          <button class="btn large" :disabled="pending" @click="confirmGoal(null)">Без передачи</button>
          <button
            v-for="p in rosterOf(sheet.teamId).filter((x: any) => x.playerId !== sheet?.player?.playerId)"
            :key="p.playerId"
            class="btn secondary pick"
            :disabled="pending"
            @click="confirmGoal(p.playerId)"
          >{{ nameOf(p) }}</button>
        </div>

        <div class="sheet-nav">
          <button class="btn ghost" type="button" @click="backSheet">Назад</button>
          <button class="btn ghost" type="button" @click="closeSheet">Закрыть</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 0.35rem;
  color: var(--muted);
  font-weight: 700;
  font-size: 0.85rem;
}
.scoreboard { display: grid; gap: 0.45rem; justify-items: center; text-align: center; border-radius: 26px 18px 22px 16px; }
.period { margin: 0; color: var(--accent); font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; font-size: 0.78rem; }
.clock {
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 9vw, 4rem);
  font-variant-numeric: tabular-nums;
  color: var(--text-strong);
  line-height: 1;
}
.clock.expired { color: var(--danger); }
.sides {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1rem;
  align-items: center;
}
.sides strong { font-family: var(--font-display); }
.sides strong:last-child { text-align: right; justify-content: flex-end; }
.club {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
.score { font-size: clamp(1.6rem, 5vw, 2.2rem); font-weight: 800; color: var(--navy); }
.controls, .events { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
.btn.goal { background: var(--navy); color: #fff; }
.btn.yellow { background: #f5c400; color: var(--navy); border-color: #e0b200; }
h2 { font-size: 1.15rem; margin: 0 0 0.35rem; }
.proto {
  display: grid;
  grid-template-columns: 64px 90px 1fr;
  gap: 0.6rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--line);
}
.proto.voided { opacity: 0.45; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: end center;
  padding: 0.75rem;
  background: rgba(0, 32, 91, 0.42);
}
.popover {
  width: min(520px, 100%);
  max-height: min(82vh, 680px);
  overflow: auto;
  background: #fff;
  border-radius: 22px 22px 16px 16px;
  padding: 1.1rem 1.15rem 1.2rem;
  box-shadow: var(--shadow);
}
.step {
  margin: 0 0 0.35rem;
  color: var(--ice);
  font-weight: 800;
  letter-spacing: 0.08em;
  font-size: 0.72rem;
}
.pick-grid { display: grid; gap: 0.6rem; margin-top: 0.9rem; }
.chip-col { display: grid; gap: 0.45rem; margin-top: 0.9rem; }
.btn.pick { justify-content: flex-start; text-align: left; }
.sheet-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 0.9rem;
}
@media (min-width: 720px) {
  .overlay { place-items: center; }
  .popover { border-radius: 20px; }
}
@media (max-width: 860px) {
  .proto { grid-template-columns: 56px 1fr; }
}
</style>
