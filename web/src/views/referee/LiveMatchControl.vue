<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../../api/client'
import { eventDetail, eventLabel, formatClock, labelOf, periodLabel, playerTag } from '../../lib/format'
import { apiError } from '../../lib/errors'
import { useMatchClock } from '../../lib/useMatchClock'
import { useTeamDirectory } from '../../lib/useTeamDirectory'
import StatusBadge from '../../components/StatusBadge.vue'

const route = useRoute()
const match = ref<any>(null)
const events = ref<any[]>([])
const homeRoster = ref<any[]>([])
const awayRoster = ref<any[]>([])
const message = ref('')
const error = ref('')
const pending = ref(false)
const assistFor = ref<any>(null)
const subFor = ref<any>(null)
const teams = useTeamDirectory()
const { elapsed, remaining, expired, cap } = useMatchClock(match)

const homeLabel = computed(() => teams.fullName(match.value?.homeTeamId, 'Хозяева'))
const awayLabel = computed(() => teams.fullName(match.value?.awayTeamId, 'Гости'))
const live = computed(() => match.value?.status === 'LIVE' || match.value?.status === 'PAUSED')

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

async function action(path: string) {
  message.value = ''
  error.value = ''
  pending.value = true
  try {
    await api.post(`/referee/matches/${route.params.id}/${path}`)
    await reload()
    message.value = path === 'finish' ? 'Финиш. Можно выдохнуть.' : 'Готово. Свисток услышан.'
  } catch (e: any) {
    error.value = apiError(e, 'Действие не прошло. Протокол не любит спешку.')
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
    assistFor.value = null
    subFor.value = null
    message.value = 'В протоколе.'
    await reload()
  } catch (e: any) {
    error.value = apiError(e, 'Не удалось записать событие')
  } finally {
    pending.value = false
  }
}

function playerEvent(type: string, teamId: string, player: any) {
  if (!live.value) {
    error.value = 'Сначала стартуйте матч.'
    return
  }
  if (type === 'GOAL') {
    assistFor.value = { teamId, player }
    return
  }
  if (type === 'SUBSTITUTION') {
    subFor.value = { teamId, player }
    return
  }
  addEvent({ eventType: type, teamId, playerId: player.playerId })
}

function confirmGoal(assistPlayerId?: string | null) {
  if (!assistFor.value) return
  addEvent({
    eventType: 'GOAL',
    teamId: assistFor.value.teamId,
    playerId: assistFor.value.player.playerId,
    secondaryPlayerId: assistPlayerId || undefined,
  })
}

function confirmSub(inPlayerId: string) {
  if (!subFor.value) return
  addEvent({
    eventType: 'SUBSTITUTION',
    teamId: subFor.value.teamId,
    playerId: subFor.value.player.playerId,
    secondaryPlayerId: inPlayerId,
  })
}

onMounted(reload)
</script>

<template>
  <section v-if="match" class="stack">
    <div class="page-title">
      <h1>Пульт судьи</h1>
      <p>Выбираете человека — и что он сделал. Время само тикает, пока вы не поставите на паузу.</p>
    </div>

    <div class="panel scoreboard" :class="{ 'live-pulse': match.status === 'LIVE' }">
      <StatusBadge :status="match.status" />
      <p class="period">{{ periodLabel(match.period, match.sportCode, match.periodCount) }}</p>
      <div class="clock" :class="{ expired }">{{ formatClock(remaining) }}</div>
      <p class="muted">осталось из {{ formatClock(cap) }} · прошло {{ formatClock(elapsed) }}</p>
      <div class="sides">
        <strong>{{ homeLabel }}</strong>
        <div class="score">{{ match.homeScore }} : {{ match.awayScore }}</div>
        <strong>{{ awayLabel }}</strong>
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

    <div v-if="assistFor" class="panel stack">
      <h2>Кто отдал голевую?</h2>
      <p class="muted">Гол: {{ playerTag(assistFor.player.displayName || `${assistFor.player.playerFirstName} ${assistFor.player.playerLastName}`, assistFor.player.jerseyNumber) }}</p>
      <div class="chip-row">
        <button class="btn secondary" :disabled="pending" @click="confirmGoal(null)">Без паса</button>
        <button
          v-for="p in (assistFor.teamId === match.homeTeamId ? homeRoster : awayRoster).filter((x: any) => x.playerId !== assistFor.player.playerId)"
          :key="p.playerId"
          class="btn ghost"
          :disabled="pending"
          @click="confirmGoal(p.playerId)"
        >{{ playerTag(p.displayName || `${p.playerFirstName} ${p.playerLastName}`, p.jerseyNumber) }}</button>
      </div>
      <button class="btn secondary" @click="assistFor = null">Отмена</button>
    </div>

    <div v-if="subFor" class="panel stack">
      <h2>Кто выходит вместо {{ playerTag(subFor.player.displayName || `${subFor.player.playerFirstName} ${subFor.player.playerLastName}`, subFor.player.jerseyNumber) }}?</h2>
      <div class="chip-row">
        <button
          v-for="p in (subFor.teamId === match.homeTeamId ? homeRoster : awayRoster).filter((x: any) => x.playerId !== subFor.player.playerId)"
          :key="p.playerId"
          class="btn ghost"
          :disabled="pending"
          @click="confirmSub(p.playerId)"
        >{{ playerTag(p.displayName || `${p.playerFirstName} ${p.playerLastName}`, p.jerseyNumber) }}</button>
      </div>
      <button class="btn secondary" @click="subFor = null">Отмена</button>
    </div>

    <div class="grid rosters">
      <div class="panel stack">
        <h2>{{ homeLabel }}</h2>
        <p v-if="!homeRoster.length" class="muted">В заявке никого. Капитан ещё собирает людей.</p>
        <div v-for="p in homeRoster" :key="p.playerId" class="row">
          <div>
            <strong>{{ playerTag(p.displayName || `${p.playerFirstName} ${p.playerLastName}`, p.jerseyNumber) }}</strong>
            <p class="muted">{{ p.position || 'игрок' }}</p>
          </div>
          <div class="acts">
            <button class="btn" :disabled="pending || !live" @click="playerEvent('GOAL', match.homeTeamId, p)">Гол</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('ASSIST', match.homeTeamId, p)">Пас</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('YELLOW_CARD', match.homeTeamId, p)">Ж</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('RED_CARD', match.homeTeamId, p)">К</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('SUBSTITUTION', match.homeTeamId, p)">↓</button>
          </div>
        </div>
      </div>
      <div class="panel stack">
        <h2>{{ awayLabel }}</h2>
        <p v-if="!awayRoster.length" class="muted">Гости тоже без заявки. Странный матч.</p>
        <div v-for="p in awayRoster" :key="p.playerId" class="row">
          <div>
            <strong>{{ playerTag(p.displayName || `${p.playerFirstName} ${p.playerLastName}`, p.jerseyNumber) }}</strong>
            <p class="muted">{{ p.position || 'игрок' }}</p>
          </div>
          <div class="acts">
            <button class="btn" :disabled="pending || !live" @click="playerEvent('GOAL', match.awayTeamId, p)">Гол</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('ASSIST', match.awayTeamId, p)">Пас</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('YELLOW_CARD', match.awayTeamId, p)">Ж</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('RED_CARD', match.awayTeamId, p)">К</button>
            <button class="btn ghost" :disabled="pending || !live" @click="playerEvent('SUBSTITUTION', match.awayTeamId, p)">↓</button>
          </div>
        </div>
      </div>
    </div>

    <div class="panel stack">
      <h2>Протокол</h2>
      <p v-if="!events.length" class="muted">Пока тихо. Первый гол всё сломает.</p>
      <div v-for="ev in [...events].reverse()" :key="ev.id" class="proto" :class="{ voided: ev.voided }">
        <span class="t">{{ formatClock(ev.gameTime) }}</span>
        <strong>{{ labelOf(eventLabel, ev.eventType) }}</strong>
        <span>{{ eventDetail(ev) || '—' }}</span>
      </div>
    </div>
    <p v-if="message" class="form-ok">{{ message }}</p>
    <p v-if="error" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.scoreboard { display: grid; gap: 0.45rem; justify-items: center; text-align: center; border-radius: 26px 18px 22px 16px; }
.period { margin: 0; color: var(--accent); font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; font-size: 0.78rem; }
.clock {
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 7vw, 3.6rem);
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
.sides strong:last-child { text-align: right; }
.controls { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
.rosters { grid-template-columns: 1fr 1fr; gap: 1rem; }
h2 { font-size: 1.15rem; }
.row {
  display: flex;
  justify-content: space-between;
  gap: 0.7rem;
  align-items: center;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--line);
}
.acts { display: flex; flex-wrap: wrap; gap: 0.3rem; justify-content: flex-end; }
.chip-row { display: flex; flex-wrap: wrap; gap: 0.45rem; }
.proto {
  display: grid;
  grid-template-columns: 64px 90px 1fr;
  gap: 0.6rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--line);
}
.proto.voided { opacity: 0.45; }
.t { color: var(--accent); font-variant-numeric: tabular-nums; }
@media (max-width: 860px) {
  .rosters { grid-template-columns: 1fr; }
  .proto { grid-template-columns: 56px 1fr; }
}
</style>
