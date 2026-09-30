<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { formatWhen } from '../lib/format'
import { apiError } from '../lib/errors'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PlayerPicker from '../components/PlayerPicker.vue'
import TeamCardEditor from '../components/TeamCardEditor.vue'
import TeamCrest from '../components/TeamCrest.vue'

const auth = useAuthStore()
const team = ref<any>(null)
const members = ref<any[]>([])
const matches = ref<any[]>([])
const schedule = ref<any[]>([])
const stats = ref<any>(null)
const playerId = ref('')
const myPlayerId = ref('')
const availability = ref<any>(null)
const opponentName = ref('')
const memberIds = computed(() => members.value.map((item) => item.playerId))
const rosterIds = computed(() => new Set(memberIds.value))
const error = ref('')
const ok = ref('')
const rsvpError = ref('')
const pending = ref(false)
const rsvpPending = ref(false)

const canEditRoster = computed(() => auth.hasRole('CAPTAIN') || auth.hasRole('ADMIN'))

const nextMatch = computed(() => {
  const now = Date.now()
  return schedule.value
    .filter((match) => match.status === 'SCHEDULED' && new Date(match.scheduledAt).getTime() >= now)
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

onMounted(load)

async function load() {
  try {
    const { data } = await api.get('/teams/mine')
    team.value = data
    const [m, cal, upcoming, st, me] = await Promise.all([
      api.get(`/teams/${data.id}/members`),
      api.get(`/teams/${data.id}/matches`, { params: { size: 30 } }),
      api.get(`/teams/${data.id}/matches`, { params: { size: 100, sort: 'scheduledAt,desc' } }),
      api.get('/statistics/teams', { params: { teamId: data.id } }),
      api.get('/players/me').catch(() => null),
    ])
    members.value = m.data
    matches.value = cal.data.content ?? []
    schedule.value = upcoming.data.content ?? []
    stats.value = (st.data ?? [])[0] || null
    myPlayerId.value = me?.data?.id || ''
    rsvpError.value = ''
    if (nextMatch.value) {
      try {
        await loadAvailability(nextMatch.value)
      } catch (e: any) {
        availability.value = null
        opponentName.value = ''
        rsvpError.value = apiError(e, 'Не удалось загрузить явку')
      }
    } else {
      availability.value = null
      opponentName.value = ''
    }
  } catch (e: any) {
    error.value = apiError(e, 'Команда ещё не назначена.')
  }
}

async function loadAvailability(match: any) {
  const opponentId = match.homeTeamId === team.value.id ? match.awayTeamId : match.homeTeamId
  const [list, opponent] = await Promise.all([
    api.get(`/matches/${match.id}/availability`),
    api.get(`/teams/${opponentId}`),
  ])
  availability.value = list.data
  opponentName.value = opponent.data?.name || ''
}

async function setRsvp(status: 'GOING' | 'NOT_GOING') {
  if (!nextMatch.value) return
  rsvpPending.value = true
  rsvpError.value = ''
  try {
    await api.post(`/matches/${nextMatch.value.id}/availability`, { status })
    await loadAvailability(nextMatch.value)
  } catch (e: any) {
    rsvpError.value = apiError(e)
  } finally {
    rsvpPending.value = false
  }
}

async function addMember() {
  pending.value = true
  error.value = ''
  try {
    await api.post(`/teams/${team.value.id}/members`, { playerId: playerId.value })
    ok.value = 'Игрок добавлен в состав.'
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}

async function removeMember(id: string) {
  pending.value = true
  try {
    await api.delete(`/teams/${team.value.id}/members/${id}`)
    await load()
  } catch (e: any) {
    error.value = apiError(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="page-title team-head">
      <TeamCrest v-if="team" :src="team.logoUrl" :name="team.name" :size="42" />
      <div>
        <h1>Моя команда</h1>
        <p v-if="team">{{ team.name }} · основана {{ team.foundedOn || '—' }}</p>
      </div>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="ok" class="form-ok">{{ ok }}</p>
    <EmptyState v-if="!team && !error" title="Команды нет" text="Админ создаёт команду и назначает капитана." />
    <template v-if="team">
      <div v-if="canEditRoster" class="panel stack">
        <h2>Карточка</h2>
        <p class="muted">Название и логотип видят в таблице и в календаре.</p>
        <TeamCardEditor :team="team" @saved="load" />
      </div>
      <div class="panel" v-if="stats">
        <h2>Статистика</h2>
        <p>В {{ stats.wins }} · Н {{ stats.draws }} · П {{ stats.losses }} · {{ stats.points }} очков</p>
      </div>
      <div class="panel">
        <h2>Состав</h2>
        <ul class="stack">
          <li v-for="m in members" :key="m.id">
            <RouterLink :to="`/players/${m.playerId}`">{{ m.displayName || `${m.firstName} ${m.lastName}` }}</RouterLink>
            <button v-if="canEditRoster && m.playerId !== team.captainId" class="btn ghost" @click="removeMember(m.playerId)">Убрать</button>
          </li>
        </ul>
        <form v-if="canEditRoster" class="toolbar" @submit.prevent="addMember">
          <PlayerPicker v-model="playerId" :exclude-ids="memberIds" />
          <button class="btn" :disabled="pending || !playerId">Добавить игрока</button>
        </form>
      </div>
      <div v-if="nextMatch" class="panel">
        <h2>Ближайший матч</h2>
        <p>
          <RouterLink :to="`/matches/${nextMatch.id}`">{{ formatWhen(nextMatch.scheduledAt) }}</RouterLink>
          <span v-if="opponentName"> · против {{ opponentName }}</span>
        </p>
        <p v-if="rsvpError" class="form-error">{{ rsvpError }}</p>
        <div class="rsvp-grid">
          <div>
            <h3>Будут · {{ going.length }}</h3>
            <ul v-if="going.length">
              <li v-for="item in going" :key="item.playerId">
                <RouterLink :to="`/players/${item.playerId}`">{{ item.displayName || 'Игрок' }}</RouterLink>
              </li>
            </ul>
            <p v-else class="muted">Пока никого</p>
          </div>
          <div>
            <h3>Не будут · {{ notGoing.length }}</h3>
            <ul v-if="notGoing.length">
              <li v-for="item in notGoing" :key="item.playerId">
                <RouterLink :to="`/players/${item.playerId}`">{{ item.displayName || 'Игрок' }}</RouterLink>
              </li>
            </ul>
            <p v-else class="muted">Пока никого</p>
          </div>
        </div>
        <div v-if="canRsvp" class="rsvp-actions">
          <button
            type="button"
            class="btn"
            :class="myStatus === 'GOING' ? 'success' : 'secondary'"
            :disabled="rsvpPending"
            @click="setRsvp('GOING')"
          >Буду</button>
          <button
            type="button"
            class="btn"
            :class="myStatus === 'NOT_GOING' ? 'danger' : 'secondary'"
            :disabled="rsvpPending"
            @click="setRsvp('NOT_GOING')"
          >Не буду</button>
        </div>
      </div>
      <div class="panel">
        <h2>Календарь команды</h2>
        <EmptyState v-if="!matches.length" title="Матчей нет" />
        <RouterLink v-for="m in matches" :key="m.id" class="row" :to="`/matches/${m.id}`">
          <StatusBadge :status="m.status" />
          <span>{{ formatWhen(m.scheduledAt) }} · {{ m.homeScore }}:{{ m.awayScore }}</span>
        </RouterLink>
      </div>
    </template>
  </section>
</template>

<style scoped>
.team-head { display: flex; align-items: center; gap: 0.8rem; }
.toolbar { display: flex; gap: 0.6rem; flex-wrap: wrap; align-items: end; margin-top: 0.8rem; }
.row { display: flex; gap: 0.7rem; align-items: center; padding: 0.45rem 0; }
.rsvp-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 0.7rem; }
.rsvp-grid h3 { margin: 0 0 0.35rem; font-size: 1rem; }
.rsvp-grid ul { margin: 0; padding-left: 1.1rem; }
.rsvp-actions { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-top: 0.95rem; }
@media (max-width: 640px) {
  .rsvp-grid { grid-template-columns: 1fr; }
}
</style>
