<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { apiError } from '../lib/errors'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PlayerPicker from '../components/PlayerPicker.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import TeamCardEditor from '../components/TeamCardEditor.vue'
import TeamCrest from '../components/TeamCrest.vue'
import { myTeamStub, showsMyTeamRoster } from '../lib/myTeamAccess'
import { matchListWhen } from '../lib/format'
import { DATE_PENDING } from '../lib/match'
import {
  calendarScoreParts,
  collectPages,
  myTeamMode,
  shouldSendRsvp,
  teamRecordLine,
} from '../lib/profileMyTeam'
import { registeredName } from '../lib/playerTitle'
import { bareLogoUrl } from '../lib/teamLogo'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const PAGE_SIZE = 100
const auth = useAuthStore()
const names = useTeamDirectory()
const team = ref<any>(null)
const members = ref<any[]>([])
const matches = ref<any[]>([])
const stats = ref<any>(null)
const playerId = ref('')
const myPlayerId = ref('')
const availability = ref<any>(null)
const opponentName = ref('')
const error = ref('')
const rosterError = ref('')
const ok = ref('')
const unassigned = ref(false)
const rsvpError = ref('')
const pending = ref(false)
const rsvpPending = ref(false)
const addOpen = ref(false)
const removeTarget = ref<{ id: string; name: string } | null>(null)
const teamState = ref<'loading' | 'ready' | 'error'>('loading')
const memberState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const calendarState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const statsState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const rsvpState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
let teamTicket = 0
let memberTicket = 0
let calendarTicket = 0
let statsTicket = 0
let rsvpTicket = 0

const rosterPage = computed(() => showsMyTeamRoster((role) => auth.hasRole(role)))
const canEditRoster = computed(() => auth.hasRole('CAPTAIN') || auth.hasRole('ADMIN'))
const memberIds = computed(() => members.value.map((item) => item.playerId))
const rosterIds = computed(() => new Set(memberIds.value))
const headerLogo = computed(() => {
  const id = team.value?.id
  return (id && names.logo(id)) || team.value?.logoUrl || ''
})
const recordLine = computed(() => (statsState.value === 'ready' ? teamRecordLine(stats.value) : ''))
const modeLine = computed(() => myTeamMode(canEditRoster.value))

const nextMatch = computed(() => {
  const now = Date.now()
  return matches.value
    .filter((match) => match.status === 'SCHEDULED' && match.scheduledAt && new Date(match.scheduledAt).getTime() >= now)
    .sort((a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime())[0] || null
})

const going = computed(() => (availability.value?.going ?? []).filter((item: any) => rosterIds.value.has(item.playerId)))
const notGoing = computed(() => (availability.value?.notGoing ?? []).filter((item: any) => rosterIds.value.has(item.playerId)))
const myStatus = computed(() => {
  const mine = [...(availability.value?.going ?? []), ...(availability.value?.notGoing ?? [])]
    .find((item) => item.playerId === myPlayerId.value)
  return mine?.status || ''
})
const canRsvp = computed(() => !!nextMatch.value && !!myPlayerId.value && rosterIds.value.has(myPlayerId.value))
const showRsvpCounts = computed(() => rsvpState.value === 'ready' && availability.value)

onMounted(() => {
  void names.load()
  if (rosterPage.value) void load()
})

function sideName(match: any, side: 'home' | 'away') {
  const embedded = side === 'home' ? match.homeTeamName : match.awayTeamName
  if (embedded) return embedded
  const id = side === 'home' ? match.homeTeamId : match.awayTeamId
  if (id && id === team.value?.id) return team.value?.name || 'Команда'
  return names.fullName(id, 'Команда')
}

function matchTitle(match: any) {
  let home = sideName(match, 'home')
  let away = sideName(match, 'away')
  if (opponentName.value && match?.id && match.id === nextMatch.value?.id) {
    if (match.homeTeamId === team.value?.id && away === 'Команда') away = opponentName.value
    if (match.awayTeamId === team.value?.id && home === 'Команда') home = opponentName.value
  }
  return `${home} – ${away}`
}

function kickoffLabel(match: any) {
  if (!match?.scheduledAt && !match?.startedAt) return DATE_PENDING
  return matchListWhen(match)
}

async function load() {
  const mine = ++teamTicket
  teamState.value = 'loading'
  error.value = ''
  unassigned.value = false
  try {
    const { data } = await api.get('/teams/mine')
    if (mine !== teamTicket) return
    if (!data?.id) {
      team.value = null
      unassigned.value = true
      teamState.value = 'ready'
      return
    }
    team.value = data
    teamState.value = 'ready'
    await Promise.all([loadMembers(), loadCalendar(), loadStats(), loadMe()])
  } catch (caught: any) {
    if (mine !== teamTicket) return
    team.value = null
    const message = apiError(caught, '')
    if (caught?.response?.status === 404 && message === 'Вы ещё не в команде') {
      unassigned.value = true
      teamState.value = 'ready'
      return
    }
    error.value = message || 'Не удалось загрузить команду'
    teamState.value = 'error'
  }
}

async function loadMembers() {
  const teamId = team.value?.id
  if (!teamId) return
  const mine = ++memberTicket
  memberState.value = 'loading'
  rosterError.value = ''
  try {
    const { data } = await api.get(`/teams/${teamId}/members`)
    if (mine !== memberTicket) return
    members.value = Array.isArray(data) ? data : []
    memberState.value = 'ready'
  } catch (caught: any) {
    if (mine !== memberTicket) return
    rosterError.value = apiError(caught, 'Не удалось загрузить состав')
    memberState.value = 'error'
  }
}

async function loadCalendar() {
  const teamId = team.value?.id
  if (!teamId) return
  const mine = ++calendarTicket
  calendarState.value = 'loading'
  try {
    const rows = await collectPages(
      (page) => api.get(`/teams/${teamId}/matches`, {
        params: { size: PAGE_SIZE, page, sort: 'scheduledAt,desc' },
      }).then(({ data }) => data),
      PAGE_SIZE,
    )
    if (mine !== calendarTicket) return
    matches.value = rows
    calendarState.value = 'ready'
    await loadAvailability()
  } catch {
    if (mine !== calendarTicket) return
    calendarState.value = 'error'
  }
}

async function loadStats() {
  const teamId = team.value?.id
  if (!teamId) return
  const mine = ++statsTicket
  statsState.value = 'loading'
  try {
    const { data } = await api.get('/statistics/teams', { params: { teamId } })
    if (mine !== statsTicket) return
    stats.value = (data ?? [])[0] || null
    statsState.value = 'ready'
  } catch {
    if (mine !== statsTicket) return
    statsState.value = 'error'
  }
}

async function loadMe() {
  try {
    const { data } = await api.get('/players/me')
    myPlayerId.value = data?.id || ''
  } catch {
    myPlayerId.value = ''
  }
}

async function loadAvailability() {
  const match = nextMatch.value
  if (!match || !team.value?.id) {
    availability.value = null
    opponentName.value = ''
    rsvpError.value = ''
    rsvpState.value = 'idle'
    return
  }
  const mine = ++rsvpTicket
  rsvpState.value = 'loading'
  try {
    const opponentId = match.homeTeamId === team.value.id ? match.awayTeamId : match.homeTeamId
    const [list, opponent] = await Promise.all([
      api.get(`/matches/${match.id}/availability`),
      api.get(`/teams/${opponentId}`),
    ])
    if (mine !== rsvpTicket) return
    availability.value = list.data
    opponentName.value = opponent.data?.name || sideName(match, match.homeTeamId === team.value.id ? 'away' : 'home')
    rsvpError.value = ''
    rsvpState.value = 'ready'
  } catch (caught: any) {
    if (mine !== rsvpTicket) return
    rsvpError.value = apiError(caught, 'Не удалось загрузить явку')
    rsvpState.value = 'error'
  }
}

async function setRsvp(status: 'GOING' | 'NOT_GOING') {
  if (!nextMatch.value || !shouldSendRsvp(myStatus.value, status, rsvpPending.value)) return
  const mine = ++rsvpTicket
  rsvpPending.value = true
  rsvpError.value = ''
  try {
    await api.post(`/matches/${nextMatch.value.id}/availability`, { status })
    if (mine !== rsvpTicket || !nextMatch.value) return
    const { data } = await api.get(`/matches/${nextMatch.value.id}/availability`)
    if (mine !== rsvpTicket) return
    availability.value = data
    rsvpState.value = 'ready'
  } catch (caught: any) {
    if (mine !== rsvpTicket) return
    rsvpError.value = apiError(caught, 'Не удалось сохранить ответ')
    rsvpState.value = 'error'
  } finally {
    if (mine === rsvpTicket) rsvpPending.value = false
  }
}

async function onCardSaved(logoUrl?: string) {
  if (logoUrl && team.value && bareLogoUrl(team.value.logoUrl) !== bareLogoUrl(logoUrl)) {
    team.value = { ...team.value, logoUrl }
  }
  try {
    const { data } = await api.get('/teams/mine')
    if (data?.id) team.value = { ...data, logoUrl: logoUrl || data.logoUrl }
  } catch {
    // The card block already shows the save result. A stale hero name can be retried.
  }
}

function openAdd() {
  playerId.value = ''
  ok.value = ''
  rosterError.value = ''
  addOpen.value = true
}

async function addMember() {
  if (!playerId.value || pending.value || !team.value?.id) return
  pending.value = true
  rosterError.value = ''
  try {
    await api.post(`/teams/${team.value.id}/members`, { playerId: playerId.value })
    ok.value = 'Игрок добавлен в состав.'
    addOpen.value = false
    playerId.value = ''
    await loadMembers()
  } catch (caught: any) {
    rosterError.value = apiError(caught, 'Не удалось добавить игрока')
  } finally {
    pending.value = false
  }
}

function askRemove(member: any) {
  if (!canEditRoster.value || member.playerId === team.value?.captainId) return
  removeTarget.value = { id: member.playerId, name: registeredName(member) || 'игрока' }
}

async function confirmRemove() {
  const target = removeTarget.value
  if (!target || pending.value || !team.value?.id) return
  pending.value = true
  rosterError.value = ''
  try {
    await api.delete(`/teams/${team.value.id}/members/${target.id}`)
    removeTarget.value = null
    ok.value = 'Игрок убран из состава.'
    await loadMembers()
  } catch (caught: any) {
    rosterError.value = apiError(caught, 'Не удалось убрать игрока')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section v-if="!rosterPage" class="team-page">
    <div class="card state">
      <h1>{{ myTeamStub.title }}</h1>
      <RouterLink class="btn" :to="myTeamStub.homeTo">{{ myTeamStub.homeLabel }}</RouterLink>
    </div>
  </section>
  <section v-else class="team-page">
    <div v-if="teamState === 'loading'" class="card skeleton" aria-hidden="true">
      <span></span><span></span><span></span>
    </div>
    <div v-else-if="teamState === 'error'" class="card state">
      <h1>Не удалось загрузить команду</h1>
      <p>{{ error }}</p>
      <button class="btn" type="button" @click="load">Повторить</button>
    </div>
    <div v-else-if="unassigned" class="card state">
      <h1>Вы пока не в команде</h1>
      <RouterLink class="btn" to="/profile">К профилю</RouterLink>
    </div>
    <template v-else-if="team">
      <header class="hero">
        <TeamCrest :src="headerLogo" :name="team.name" :size="64" />
        <div class="who">
          <p class="kicker">Моя команда</p>
          <h1>{{ team.name }}</h1>
          <p>{{ modeLine }}</p>
          <p v-if="recordLine">{{ recordLine }}</p>
          <p v-else-if="statsState === 'error'" class="stats-miss">
            Не удалось загрузить статистику
            <button type="button" @click="loadStats">Повторить</button>
          </p>
        </div>
      </header>

      <div class="board" :class="{ manage: canEditRoster }">
        <section class="card block match" data-section="match">
          <h2>Ближайший матч</h2>
          <div v-if="calendarState === 'loading'" class="skeleton thin" aria-hidden="true"><span></span><span></span></div>
          <div v-else-if="calendarState === 'error'" class="block-error">
            <p>Не удалось загрузить матчи</p>
            <button class="btn" type="button" @click="loadCalendar">Повторить</button>
          </div>
          <p v-else-if="!nextMatch" class="quiet">Ближайших матчей пока нет</p>
          <template v-else>
            <p class="when">{{ kickoffLabel(nextMatch) }}</p>
            <p class="title">{{ matchTitle(nextMatch) }}</p>
            <p v-if="showRsvpCounts" class="counts">Будут: {{ going.length }} · Не будут: {{ notGoing.length }}</p>
            <p v-else-if="rsvpState === 'loading'" class="quiet">Загрузка явки…</p>
            <p v-if="rsvpState === 'error'" class="form-error">{{ rsvpError }}</p>
            <p v-if="myStatus" class="picked">Ваш ответ: {{ myStatus === 'GOING' ? 'буду' : 'не буду' }}</p>
            <div v-if="canRsvp" class="rsvp">
              <button
                type="button"
                class="btn"
                :aria-pressed="myStatus === 'GOING'"
                :disabled="rsvpPending"
                @click="setRsvp('GOING')"
              >Буду</button>
              <button
                type="button"
                class="btn secondary"
                :aria-pressed="myStatus === 'NOT_GOING'"
                :disabled="rsvpPending"
                @click="setRsvp('NOT_GOING')"
              >Не буду</button>
            </div>
          </template>
        </section>

        <section v-if="canEditRoster" class="card block card-edit" data-section="card">
          <h2>Карточка команды</h2>
          <TeamCardEditor :team="team" @saved="onCardSaved" />
        </section>

        <section class="card block roster" data-section="roster">
          <h2>Состав</h2>
          <p v-if="ok" class="form-ok">{{ ok }}</p>
          <div v-if="memberState === 'loading'" class="skeleton thin" aria-hidden="true"><span></span><span></span></div>
          <div v-else-if="memberState === 'error'" class="block-error">
            <p>{{ rosterError || 'Не удалось загрузить состав' }}</p>
            <button class="btn" type="button" @click="loadMembers">Повторить</button>
          </div>
          <ul v-else class="people">
            <li v-for="member in members" :key="member.id || member.playerId">
              <PlayerAvatar
                :name="registeredName(member)"
                :first-name="member.playerFirstName"
                :last-name="member.playerLastName"
                :size="40"
              />
              <span class="person">
                <RouterLink :to="`/players/${member.playerId}`">{{ registeredName(member) || 'Игрок' }}</RouterLink>
                <small v-if="member.playerId === team.captainId">Капитан</small>
              </span>
              <button
                v-if="canEditRoster && member.playerId !== team.captainId"
                class="btn secondary slim"
                type="button"
                :disabled="pending"
                @click="askRemove(member)"
              >Убрать</button>
            </li>
          </ul>
          <p v-if="memberState === 'ready' && !members.length" class="quiet">В составе пока никого</p>
          <button v-if="canEditRoster" class="btn secondary wide" type="button" @click="openAdd">Добавить игрока</button>
          <p v-if="rosterError && memberState === 'ready' && !removeTarget && !addOpen" class="form-error">{{ rosterError }}</p>
        </section>

        <section class="card block calendar" data-section="calendar">
          <h2>Календарь команды</h2>
          <div v-if="calendarState === 'loading'" class="skeleton thin" aria-hidden="true"><span></span><span></span></div>
          <div v-else-if="calendarState === 'error'" class="block-error">
            <p>Не удалось загрузить календарь</p>
            <button class="btn" type="button" @click="loadCalendar">Повторить</button>
          </div>
          <EmptyState v-else-if="!matches.length" title="Матчей пока нет" text="Сыгранные и будущие матчи появятся здесь" />
          <RouterLink v-for="match in matches" :key="match.id" class="cal" :to="`/matches/${match.id}`">
            <span class="when">{{ kickoffLabel(match) }}</span>
            <span class="opponent">
              {{ matchTitle(match) }}
              <StatusBadge :status="match.status" :at="match.scheduledAt ?? null" :third-place="!!match.thirdPlace" />
            </span>
            <span class="score">
              <span class="main">{{ calendarScoreParts(match).score }}</span>
              <small v-if="calendarScoreParts(match).penalty" class="pens">{{ calendarScoreParts(match).penalty }}</small>
            </span>
          </RouterLink>
        </section>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="addOpen" class="overlay" @click.self="addOpen = false">
        <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="add-player-title">
          <div class="dialog-head">
            <h2 id="add-player-title">Добавить игрока</h2>
            <button class="btn secondary" type="button" @click="addOpen = false">Закрыть</button>
          </div>
          <PlayerPicker
            v-model="playerId"
            :exclude-ids="memberIds"
            hint="Найти зарегистрированного игрока"
            empty-text="Игрок не найден, он должен сначала зарегистрироваться"
          />
          <p v-if="rosterError" class="form-error">{{ rosterError }}</p>
          <button class="btn" type="button" :disabled="pending || !playerId" @click="addMember">Добавить</button>
          <button class="btn ghost" type="button" @click="addOpen = false">Отмена</button>
        </div>
      </div>
      <div v-if="removeTarget" class="overlay" @click.self="removeTarget = null">
        <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="remove-player-title">
          <h2 id="remove-player-title">Убрать «{{ removeTarget.name }}» из команды?</h2>
          <p v-if="rosterError" class="form-error">{{ rosterError }}</p>
          <div class="confirm">
            <button class="btn secondary" type="button" :disabled="pending" @click="removeTarget = null">Отмена</button>
            <button class="btn danger" type="button" :disabled="pending" @click="confirmRemove">Убрать из команды</button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.team-page { min-width: 0; max-width: 100%; display: grid; gap: 16px; }
.hero {
  display: flex;
  gap: 0.9rem;
  align-items: center;
  min-width: 0;
  background: #001433;
  color: #fff;
  border-radius: 22px;
  padding: 1.15rem 1.2rem;
}
.who { min-width: 0; }
.kicker, .who p { margin: 0.15rem 0 0; color: rgba(255, 255, 255, 0.82); }
.hero h1 {
  margin: 0;
  color: #fff;
  font-size: 2rem;
  line-height: 1.05;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}
.stats-miss { display: flex; flex-wrap: wrap; gap: 0.45rem; align-items: center; }
.stats-miss button {
  min-height: 44px;
  border: 0;
  border-radius: 10px;
  background: #fff;
  color: var(--navy);
  padding: 0.3rem 0.7rem;
  font-weight: 800;
  cursor: pointer;
}
.board { display: grid; gap: 16px; min-width: 0; }
.card {
  min-width: 0;
  background: #fff;
  border-radius: 18px;
  padding: 1rem 1.05rem 1.05rem;
  box-shadow: var(--shadow);
}
.card h2 {
  margin: 0 0 0.7rem;
  color: var(--navy);
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.state { display: grid; justify-items: start; gap: 0.8rem; }
.state h1 { margin: 0; color: var(--navy); font-size: 1.4rem; }
.when { margin: 0; color: var(--muted); }
.title { margin: 0.25rem 0 0; color: var(--navy); font-size: 1.35rem; font-weight: 800; overflow-wrap: anywhere; }
.counts, .picked, .quiet { margin: 0.45rem 0 0; color: var(--navy); }
.quiet { color: var(--muted); }
.rsvp, .confirm { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-top: 0.8rem; }
.rsvp .btn, .wide, .slim { min-height: 44px; }
.wide { width: 100%; margin-top: 0.8rem; }
.rsvp .btn[aria-pressed="true"] { box-shadow: inset 0 0 0 2px var(--navy); }
.people { list-style: none; margin: 0; padding: 0; display: grid; }
.people li {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-width: 0;
  min-height: 52px;
  padding: 0.45rem 0;
  border-top: 1px solid var(--line);
}
.person { min-width: 0; flex: 1; display: grid; }
.person a { color: var(--navy); font-weight: 800; overflow-wrap: anywhere; }
.person small { color: var(--muted); }
.slim { flex: 0 0 auto; }
.cal {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.35rem 0.75rem;
  align-items: center;
  min-height: 44px;
  padding: 0.75rem 0;
  border-top: 1px solid var(--line);
  color: var(--navy);
  text-decoration: none;
}
.opponent { min-width: 0; display: flex; flex-wrap: wrap; gap: 0.35rem 0.55rem; align-items: center; overflow-wrap: break-word; font-weight: 800; }
.calendar .score {
  display: grid;
  justify-items: end;
  gap: 0.05rem;
  white-space: nowrap;
  font-weight: 800;
  text-align: right;
  line-height: 1.15;
}
.calendar .score .main { font-size: 1rem; font-weight: 800; }
.calendar .score .pens {
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1.2;
}
.block-error { display: grid; justify-items: start; gap: 0.65rem; }
.block-error p { margin: 0; color: var(--navy); font-weight: 800; }
.skeleton { display: grid; gap: 0.5rem; }
.skeleton span {
  display: block;
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e3ebf3, #f7fafc, #e3ebf3);
}
.skeleton span:first-child { width: 46%; height: 22px; }
.overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 0.75rem;
  background: rgba(0, 32, 91, 0.55);
}
.sheet {
  width: min(560px, 100%);
  max-height: min(90dvh, 720px);
  overflow: auto;
  display: grid;
  gap: 0.75rem;
  background: #fff;
  border-radius: 20px;
  padding: 1rem 1.1rem 1.15rem;
}
.dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; }
.sheet h2 { margin: 0; color: var(--navy); overflow-wrap: anywhere; }
@media (min-width: 720px) {
  .team-page { gap: 24px; }
  .board.manage {
    grid-template-columns: minmax(0, 1fr) minmax(260px, 360px);
    gap: 24px;
    align-items: start;
  }
  .board.manage .match { grid-column: 1; grid-row: 1; }
  .board.manage .roster { grid-column: 1; grid-row: 2; }
  .board.manage .card-edit { grid-column: 2; grid-row: 1 / span 2; }
  .board.manage .calendar { grid-column: 1 / -1; }
  .board:not(.manage) { gap: 24px; }
}
@media (max-width: 719px) {
  .hero { border-radius: 16px; padding: 0.95rem 0.85rem; }
  .hero h1 { font-size: 1.45rem; }
  .match { order: 1; }
  .roster { order: 2; }
  .card-edit { order: 3; }
  .calendar { order: 4; }
  .title { font-size: 1.15rem; }
  .cal { grid-template-columns: minmax(0, 1fr) auto; align-items: start; }
  .opponent { grid-column: 1 / -1; }
  .score { text-align: right; }
  .overlay { place-items: end stretch; padding: 0.5rem; }
  .sheet { width: 100%; max-height: calc(100dvh - 0.75rem); border-radius: 18px 18px 12px 12px; }
}
</style>
